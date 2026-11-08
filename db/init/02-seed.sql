SET NAMES utf8mb4;

INSERT INTO template
  (id, title, category, description, cover_path, required_image_count, accepted_mime_types, max_file_size_bytes, layout_key, enabled)
VALUES
  ('travel-postcard', '旅行明信片', '旅行纪念', '上传两张照片，模拟制作一张旅行明信片。', 'demo/travel-postcard.svg', 2, JSON_ARRAY('image/jpeg', 'image/png'), 5242880, 'POSTCARD_TWO', TRUE),
  ('pet-portrait', '宠物画像', '宠物日常', '上传一张照片，模拟制作宠物主题画像。', 'demo/pet-portrait.svg', 1, JSON_ARRAY('image/jpeg', 'image/png'), 5242880, 'PORTRAIT_ONE', TRUE),
  ('memory-collage', '回忆拼贴', '旅行纪念', '上传两张照片，模拟制作一张双图拼贴。', 'demo/memory-collage.svg', 2, JSON_ARRAY('image/jpeg', 'image/png'), 5242880, 'COLLAGE_TWO', TRUE)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  category = VALUES(category),
  description = VALUES(description),
  cover_path = VALUES(cover_path),
  required_image_count = VALUES(required_image_count),
  accepted_mime_types = VALUES(accepted_mime_types),
  max_file_size_bytes = VALUES(max_file_size_bytes),
  layout_key = VALUES(layout_key),
  enabled = VALUES(enabled);
