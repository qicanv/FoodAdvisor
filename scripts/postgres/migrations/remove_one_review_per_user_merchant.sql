-- 移除"一用户一商家一原始评价"的唯一约束，允许用户对同一商家发表多条原始评价
DROP INDEX IF EXISTS uk_reviews_user_merchant_original;
