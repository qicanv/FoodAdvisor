"""
内容清洗与切分服务

职责:
1. 文本清洗 — HTML 标签、控制字符、重复空格、无意义内容、日期/价格标准化
2. 文本切分 — 按段落+句子边界智能切分，超长时保留上下文重叠
3. 批量处理 — 单条异常隔离，失败不影响其他数据
4. 确定性输出 — 相同输入+配置 → 相同 chunkId（SHA256 哈希）

设计原则:
- 清洗顺序固定：HTML → 控制字符 → 日期/价格 → 无意义内容 → 空格合并
- 切分优先按自然边界（段落 > 句子），仅超长时强制长度切分
- 每个 chunk 携带完整来源信息（merchantId, sourceType, sourceId, timestamp）
"""
import hashlib
import logging
import re
from typing import List, Optional

from app.schemas.content_processing import (
    ContentItem,
    ContentChunk,
    ProcessRequest,
    ProcessResult,
    ProcessError,
    CleanConfig,
    ChunkConfig,
    SourceTypeEnum,
)

logger = logging.getLogger(__name__)

# ============================================
# 无意义内容模式 — 匹配到的内容会被移除
# ============================================
MEANINGLESS_PATTERNS: list[tuple[re.Pattern, str]] = [
    (re.compile(r"该用户没有填写.*"), ""),
    (re.compile(r"默认好评"), ""),
    (re.compile(r"系统自动(生成|评价|回复).*"), ""),
    (re.compile(r"此用户未.*评价"), ""),
    (re.compile(r"匿名用户"), ""),
    (re.compile(r"^\d{1,2}字评价$"), ""),
    (re.compile(r"<script[^>]*>.*?</script>", re.DOTALL), ""),
    (re.compile(r"<style[^>]*>.*?</style>", re.DOTALL), ""),
]


# ============================================
# HTML 实体解码映射
# ============================================
HTML_ENTITIES: dict[str, str] = {
    "&nbsp;": " ",
    "&amp;": "&",
    "&lt;": "<",
    "&gt;": ">",
    "&quot;": '"',
    "&#39;": "'",
    "&apos;": "'",
    "&#x27;": "'",
    "&mdash;": "—",
    "&ndash;": "–",
    "&hellip;": "…",
    "&ldquo;": '"',
    "&rdquo;": '"',
    "&lsquo;": "'",
    "&rsquo;": "'",
}


class ContentProcessingService:
    """内容清洗与切分服务"""

    # ============================================
    # 文本清洗
    # ============================================

    def clean(self, text: str, config: CleanConfig) -> str:
        """
        清洗文本。

        执行顺序（不可颠倒）：
        1. 去除 HTML 标签 + 实体解码
        2. 去除控制字符（保留 \\n 用于后续切分）
        3. 标准化日期格式
        4. 标准化价格格式
        5. 去除无意义内容
        6. 合并多余空格/换行
        7. 自定义替换
        """
        result = text

        # ---- 1. HTML 清洗 ----
        if config.removeHtmlTags:
            # 先移除 script/style 标签及其内容
            result = re.sub(r"<script[^>]*>.*?</script>", " ", result, flags=re.DOTALL | re.IGNORECASE)
            result = re.sub(r"<style[^>]*>.*?</style>", " ", result, flags=re.DOTALL | re.IGNORECASE)
            # 移除其余 HTML 标签
            result = re.sub(r"<[^>]+>", " ", result)
            # 解码常见 HTML 实体
            for entity, char in HTML_ENTITIES.items():
                result = result.replace(entity, char)
            # 处理数字实体 &#\d+;
            result = re.sub(r"&#(\d+);", lambda m: chr(int(m.group(1))) if int(m.group(1)) < 0x110000 else " ", result)

        # ---- 2. 控制字符 ----
        if config.removeControlChars:
            # 统一换行符
            result = result.replace("\r\n", "\n").replace("\r", "\n")
            # 去除除 \n \t 以外的控制字符
            result = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]", "", result)

        # ---- 3. 日期标准化 ----
        if config.normalizeDates:
            result = self._normalize_dates(result)

        # ---- 4. 价格标准化 ----
        if config.normalizePrices:
            result = self._normalize_prices(result)

        # ---- 5. 无意义内容 ----
        if config.removeMeaninglessPatterns:
            for pattern, replacement in MEANINGLESS_PATTERNS:
                result = pattern.sub(replacement, result)

        # ---- 6. 空格/换行合并 ----
        if config.collapseSpaces:
            # 合并空格和制表符
            result = re.sub(r"[ \t]+", " ", result)
            # 合并 3 个及以上连续换行为 2 个
            result = re.sub(r"\n{3,}", "\n\n", result)
            # 去除行首行尾空格（保留换行结构）
            result = "\n".join(line.strip() for line in result.split("\n"))

        # ---- 7. 自定义替换 ----
        for old, new in config.customReplacements.items():
            result = result.replace(old, new)

        return result.strip()

    # ---- 日期标准化 ----

    @staticmethod
    def _normalize_dates(text: str) -> str:
        """
        将中文/数字日期统一为 YYYY-MM-DD 格式。

        支持格式：
        - 2024年1月5日  → 2024-01-05
        - 2024/1/5      → 2024-01-05
        - 2024.1.5      → 2024-01-05
        - 2024-1-5      → 2024-01-05
        """
        def _pad_date(m: re.Match) -> str:
            y, mo, d = m.group(1), m.group(2), m.group(3)
            return f"{y}-{int(mo):02d}-{int(d):02d}"

        text = re.sub(r"(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日", _pad_date, text)
        text = re.sub(r"(\d{4})/(\d{1,2})/(\d{1,2})", _pad_date, text)
        text = re.sub(r"(\d{4})\.(\d{1,2})\.(\d{1,2})", _pad_date, text)
        text = re.sub(r"(\d{4})-(\d{1,2})-(\d{1,2})", _pad_date, text)
        return text

    # ---- 价格标准化 ----

    @staticmethod
    def _normalize_prices(text: str) -> str:
        """
        统一价格格式为 ¥XX。

        支持格式：
        - 50元 / 50 元     → ¥50
        - 50块钱 / 50块     → ¥50
        - RMB 50 / RMB50   → ¥50
        - 人均50 / 人均 50  → 人均¥50
        """
        text = re.sub(r"(\d+\.?\d*)\s*元", r"¥\1", text)
        text = re.sub(r"(\d+\.?\d*)\s*块(?:钱)?", r"¥\1", text)
        text = re.sub(r"RMB\s*(\d+\.?\d*)", r"¥\1", text, flags=re.IGNORECASE)
        return text

    # ============================================
    # 文本切分
    # ============================================

    def chunk_text(self, text: str, config: ChunkConfig) -> list[str]:
        """
        将清洗后文本切分为块。

        切分策略（按优先级）：
        1. 按分隔符（默认 \\n）拆分为段落
        2. 段落长度 <= maxChunkLength → 合并到当前块
        3. 段落长度 > maxChunkLength → 按句子边界切分
        4. 句子仍超长 → 强制按长度切分
        5. 块间保留 overlapLength 字符重叠
        """
        if not text or not text.strip():
            return []

        max_len = config.maxChunkLength
        overlap = config.overlapLength
        separator = config.separator

        # Step 1: 按分隔符拆分为段落
        paragraphs = text.split(separator) if separator else [text]
        paragraphs = [p.strip() for p in paragraphs if p.strip()]

        if not paragraphs:
            return []

        # Step 2: 合并段落为块
        chunks: list[str] = []
        current = ""

        for para in paragraphs:
            # 段落本身超长 → 先处理当前块，再单独切分长段落
            if len(para) > max_len:
                # 保存当前块
                if current.strip():
                    chunks.append(current.strip())
                    current = ""
                # 将长段落按句子/长度切分
                sub_chunks = self._split_long_paragraph(para, config)
                chunks.extend(sub_chunks)
                continue

            # 尝试追加到当前块
            candidate = current + ("\n" if current else "") + para
            if len(candidate) <= max_len:
                current = candidate
            else:
                # 当前块已满，保存并开始新块
                if current.strip():
                    chunks.append(current.strip())
                # 新块开头加入重叠文本
                if overlap > 0 and current:
                    overlap_text = current[-overlap:]
                    current = overlap_text + "\n" + para
                else:
                    current = para

        # 最后一个块
        if current.strip():
            chunks.append(current.strip())

        return chunks

    def _split_long_paragraph(self, text: str, config: ChunkConfig) -> list[str]:
        """
        切分超长段落。

        优先在句子边界（。！？!? ；， ,;）处切分，
        单句仍超长时强制按长度切分。
        """
        max_len = config.maxChunkLength
        overlap = config.overlapLength

        if config.preserveSentenceBoundary:
            # 在句子边界处拆分
            sentences = re.split(r"(?<=[。！？!?])\s*", text)
            # 对未正确拆分的部分再进行逗号/分号边界补充
            refined: list[str] = []
            for s in sentences:
                if not s.strip():
                    continue
                if len(s) > max_len:
                    # 尝试在逗号/分号处进一步拆分
                    parts = re.split(r"(?<=[，,；;])\s*", s)
                    refined.extend(p for p in parts if p.strip())
                else:
                    refined.append(s)
            sentences = refined
        else:
            # 不关心句子边界，直接按长度均分
            sentences = [
                text[i:i + max_len - overlap]
                for i in range(0, len(text), max_len - overlap)
            ]

        # 合并句子为不超过 max_len 的块
        chunks: list[str] = []
        current = ""

        for sent in sentences:
            if not sent.strip():
                continue

            # 单句超长 → 强制按长度切分
            if len(sent) > max_len:
                if current.strip():
                    chunks.append(current.strip())
                    current = ""
                for i in range(0, len(sent), max_len - overlap):
                    piece = sent[i:i + max_len].strip()
                    if piece:
                        chunks.append(piece)
                continue

            # 尝试追加
            if len(current) + len(sent) <= max_len:
                current += sent
            else:
                if current.strip():
                    chunks.append(current.strip())
                # 重叠
                if overlap > 0 and current:
                    current = current[-overlap:] + sent
                else:
                    current = sent

        if current.strip():
            chunks.append(current.strip())

        return chunks

    # ============================================
    # 批量处理
    # ============================================

    def process(self, request: ProcessRequest) -> ProcessResult:
        """
        批量清洗+切分。

        每条 ContentItem 独立处理：
        - 清洗 → 切分 → 组装 ContentChunk（带来源信息）
        - 单条失败仅记录错误，不中断其他数据的处理
        """
        all_chunks: list[ContentChunk] = []
        errors: list[ProcessError] = []

        for item in request.items:
            try:
                # Step 1: 清洗
                cleaned = self.clean(item.content, request.cleanConfig)

                # Step 2: 切分
                text_pieces = self.chunk_text(cleaned, request.chunkConfig)

                # 若切分结果为空（清洗后无有效内容），保留清洗后内容为单块
                if not text_pieces:
                    if cleaned:
                        text_pieces = [cleaned]
                    else:
                        continue  # 完全无内容，跳过

                # Step 3: 构建 chunk 对象
                total = len(text_pieces)
                for idx, chunk_text in enumerate(text_pieces):
                    chunk = ContentChunk(
                        chunkId=self._generate_chunk_id(
                            item.merchantId, item.sourceType, item.sourceId, idx
                        ),
                        merchantId=item.merchantId,
                        sourceType=item.sourceType,
                        sourceId=item.sourceId,
                        sourceTimestamp=item.sourceTimestamp,
                        chunkIndex=idx,
                        totalChunks=total,
                        rawText=item.content,
                        cleanedText=chunk_text,
                        metadata={
                            **item.metadata,
                            "cleanConfigHash": self._hash_config(request.cleanConfig),
                        },
                    )
                    all_chunks.append(chunk)

            except Exception as e:
                logger.error(
                    "内容处理失败 merchantId=%s sourceType=%s sourceId=%s: %s",
                    item.merchantId,
                    item.sourceType.value if item.sourceType else "?",
                    item.sourceId,
                    e,
                    exc_info=True,
                )
                errors.append(ProcessError(
                    merchantId=item.merchantId,
                    sourceType=item.sourceType.value if item.sourceType else "UNKNOWN",
                    sourceId=item.sourceId,
                    error=str(e)[:500],
                ))

        return ProcessResult(
            requestId=request.requestId,
            totalItems=len(request.items),
            successCount=len(request.items) - len(errors),
            failCount=len(errors),
            totalChunks=len(all_chunks),
            chunks=all_chunks,
            errors=errors,
        )

    # ============================================
    # 确定性 Chunk ID 生成
    # ============================================

    @staticmethod
    def _generate_chunk_id(
        merchant_id: int,
        source_type: SourceTypeEnum,
        source_id: int,
        chunk_index: int,
    ) -> str:
        """
        确定性生成 chunk ID。

        使用 SHA256 哈希确保：
        - 相同输入始终生成相同 ID
        - 不同输入碰撞概率极低
        - 分布式环境下无需中心协调
        """
        seed = f"{merchant_id}:{source_type.value}:{source_id}:{chunk_index}"
        hash_hex = hashlib.sha256(seed.encode("utf-8")).hexdigest()[:16]
        return f"chunk-{merchant_id}-{hash_hex}"

    @staticmethod
    def _hash_config(config: CleanConfig) -> str:
        """生成清洗配置的哈希摘要（用于 chunk metadata 追溯）"""
        raw = config.model_dump_json(exclude={"customReplacements"})
        return hashlib.sha256(raw.encode()).hexdigest()[:12]

    # ============================================
    # 辅助：单条处理（供其他服务直接调用）
    # ============================================

    def process_single(
        self,
        merchant_id: int,
        source_type: SourceTypeEnum,
        source_id: int,
        content: str,
        source_timestamp: Optional[str] = None,
        clean_config: Optional[CleanConfig] = None,
        chunk_config: Optional[ChunkConfig] = None,
        metadata: Optional[dict] = None,
    ) -> tuple[list[ContentChunk], Optional[str]]:
        """
        便捷方法：处理单条内容。

        返回:
            (chunks, error) — 成功时 error=None；失败时 chunks=[] 且 error 为错误信息
        """
        item = ContentItem(
            merchantId=merchant_id,
            sourceType=source_type,
            sourceId=source_id,
            sourceTimestamp=source_timestamp,
            content=content,
            metadata=metadata or {},
        )
        request = ProcessRequest(
            items=[item],
            cleanConfig=clean_config or CleanConfig(),
            chunkConfig=chunk_config or ChunkConfig(),
        )
        result = self.process(request)
        if result.errors:
            return [], result.errors[0].error
        return result.chunks, None


# 模块级单例
content_processing_service = ContentProcessingService()
