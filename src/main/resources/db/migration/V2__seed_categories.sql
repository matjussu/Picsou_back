-- 15 catégories par défaut (user_id NULL = visibles pour tous les users)
INSERT INTO categories (id, user_id, name, icon_key, color_key, is_default) VALUES
  (gen_random_uuid(), NULL, 'Loyer', 'home', 'blue', TRUE),
  (gen_random_uuid(), NULL, 'Courses', 'shopping-cart', 'green', TRUE),
  (gen_random_uuid(), NULL, 'Restaurant', 'utensils', 'orange', TRUE),
  (gen_random_uuid(), NULL, 'Loisirs', 'gamepad', 'purple', TRUE),
  (gen_random_uuid(), NULL, 'Transport', 'car', 'red', TRUE),
  (gen_random_uuid(), NULL, 'Streaming & Abonnements', 'play', 'pink', TRUE),
  (gen_random_uuid(), NULL, 'Santé', 'heart', 'rose', TRUE),
  (gen_random_uuid(), NULL, 'Vêtements', 'shirt', 'amber', TRUE),
  (gen_random_uuid(), NULL, 'Éducation', 'book', 'teal', TRUE),
  (gen_random_uuid(), NULL, 'Cadeaux', 'gift', 'fuchsia', TRUE),
  (gen_random_uuid(), NULL, 'Salaire', 'briefcase', 'lime', TRUE),
  (gen_random_uuid(), NULL, 'Bourse / BAF', 'graduation-cap', 'cyan', TRUE),
  (gen_random_uuid(), NULL, 'Remboursement', 'rotate-ccw', 'sky', TRUE),
  (gen_random_uuid(), NULL, 'Virement reçu', 'arrow-down', 'emerald', TRUE),
  (gen_random_uuid(), NULL, 'Autre', 'circle', 'slate', TRUE);
