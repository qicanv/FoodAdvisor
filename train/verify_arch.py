"""
模型架构验证脚本：在正式训练前快速验证模型能正常前向传播

用法（PyTorch 安装完成后运行）:
    cd train && python verify_arch.py
"""

import torch
from model import MultiHeadSentimentClassifier


def test_forward_pass():
    """验证模型前向传播的输入输出形状是否正确"""
    print("=" * 60)
    print("模型架构验证")
    print("=" * 60)

    # ---- 1. 初始化模型 ----
    # 使用与正式训练相同的模型，避免下载两个不同的模型
    from config import BASE_MODEL
    print(f"\n[1] 初始化模型: {BASE_MODEL}")
    model = MultiHeadSentimentClassifier(base_model_name=BASE_MODEL)
    print(f"    维度: {model.dimensions}")
    print(f"    编码器隐藏层大小: {model.config.hidden_size}")
    print(f"    分类头: {list(model.classifiers.keys())}")

    # 统计参数量
    total = sum(p.numel() for p in model.parameters())
    trainable = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"    总参数量: {total:,}")
    print(f"    可训练参数量: {trainable:,}")

    # ---- 2. 模拟前向传播 ----
    print("\n[2] 模拟前向传播 (batch_size=4, max_length=256)...")
    batch_size = 4
    seq_len = 256
    dummy_input_ids = torch.randint(0, 21128, (batch_size, seq_len))
    dummy_attention_mask = torch.ones(batch_size, seq_len, dtype=torch.long)

    # 不传 labels
    outputs = model(dummy_input_ids, dummy_attention_mask)
    print("    (无标签模式) 输出维度:")
    for dim in model.dimensions:
        logits_shape = outputs[dim]["logits"].shape
        probs_shape = outputs[dim]["probs"].shape
        preds_shape = outputs[dim]["pred"].shape
        print(f"      {dim}: logits={list(logits_shape)}, "
              f"probs={list(probs_shape)}, pred={list(preds_shape)}")

    assert outputs["overall"]["logits"].shape == (4, 4), "logits 形状错误!"
    assert outputs["overall"]["probs"].shape == (4, 4), "probs 形状错误!"
    assert outputs["overall"]["pred"].shape == (4,), "pred 形状错误!"
    print("    ✅ 形状验证通过")

    # ---- 3. 模拟带标签训练 ----
    print("\n[3] 模拟训练步骤 (含损失计算)...")
    dummy_labels = {
        "overall": torch.randint(0, 4, (batch_size,)),
        "service": torch.randint(0, 4, (batch_size,)),
        "dish": torch.randint(0, 4, (batch_size,)),
    }
    outputs = model(dummy_input_ids, dummy_attention_mask, labels=dummy_labels)
    loss = outputs["loss"]
    print(f"    loss: {loss.item():.4f}")
    print(f"    loss requires_grad: {loss.requires_grad}")

    # 反向传播
    loss.backward()
    print("    ✅ 反向传播通过")

    # ---- 4. 类别权重 ----
    print("\n[4] 设置类别权重...")
    weights = {
        "overall": [17.52, 2.55, 1.31, 0.36],
        "service": [0.83, 1.65, 2.22, 0.58],
        "dish": [8.10, 1.60, 1.45, 0.39],
    }
    model.set_class_weights(weights)
    for dim in model.dimensions:
        w = model.loss_fns[dim].weight
        print(f"    {dim}: {w.tolist()}")
    print("    ✅ 权重设置通过")

    # ---- 5. predict 方法 ----
    print("\n[5] 验证 predict() 方法...")
    single_input = torch.randint(0, 21128, (1, seq_len))
    single_mask = torch.ones(1, seq_len, dtype=torch.long)
    result = model.predict(single_input, single_mask)
    for dim in model.dimensions:
        assert "label" in result[dim]
        assert "label_name" in result[dim]
        assert "confidence" in result[dim]
        print(f"    {dim}: label={result[dim]['label']}, "
              f"name={result[dim]['label_name']}, "
              f"conf={result[dim]['confidence']}")
    print("    ✅ predict() 通过")

    # ---- 6. 保存 & 加载 ----
    print("\n[6] 验证 save() / load()...")
    import tempfile
    import os
    with tempfile.TemporaryDirectory() as tmpdir:
        model.save(tmpdir)
        assert os.path.exists(os.path.join(tmpdir, "pytorch_model.bin"))
        assert os.path.exists(os.path.join(tmpdir, "model_config.json"))

        loaded = MultiHeadSentimentClassifier.load(tmpdir)
        assert loaded.dimensions == model.dimensions
        print(f"    ✅ save/load 通过 (临时目录: {tmpdir})")

    print("\n" + "=" * 60)
    print("✅ 全部验证通过！模型架构正确，可以开始训练。")
    print("=" * 60)


if __name__ == "__main__":
    test_forward_pass()
