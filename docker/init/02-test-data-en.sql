USE secondhand_market;
SET NAMES utf8mb4;

-- Add test users
INSERT INTO t_user (username, password, nickname, role) VALUES
('user001', '0192023a7bbd73250516f069df18b500', 'user1', 1),
('user002', '0192023a7bbd73250516f069df18b500', 'user2', 1),
('user003', '0192023a7bbd73250516f069df18b500', 'user3', 1),
('user004', '0192023a7bbd73250516f069df18b500', 'user4', 1),
('user005', '0192023a7bbd73250516f069df18b500', 'user5', 1);

-- Category 1: Books
INSERT INTO t_product (name, price, image_url, description, category_id, user_id, view_count) VALUES
('Math Book Volume 1', 25.00, 'upload/placeholder.jpg', 'Used math textbook, good condition', 1, 1, 128),
('Linear Algebra', 20.00, 'upload/placeholder.jpg', 'English version textbook', 1, 2, 89),
('Data Structures', 35.00, 'upload/placeholder.jpg', 'Algorithm textbook with exercises', 1, 3, 203),
('Java Programming', 45.00, 'upload/placeholder.jpg', 'Java learning book', 1, 4, 312),
('Computer Networks', 30.00, 'upload/placeholder.jpg', 'Network textbook', 1, 5, 156),
('Operating Systems', 38.00, 'upload/placeholder.jpg', 'OS textbook', 1, 1, 98),
('Database Systems', 28.00, 'upload/placeholder.jpg', 'Database textbook', 1, 2, 145),
('Software Engineering', 22.00, 'upload/placeholder.jpg', 'SE introduction', 1, 3, 76),
('Python Programming', 32.00, 'upload/placeholder.jpg', 'Python beginner book', 1, 4, 267),
('Machine Learning', 42.00, 'upload/placeholder.jpg', 'ML practical guide', 1, 5, 189);

-- Category 2: Electronics
INSERT INTO t_product (name, price, image_url, description, category_id, user_id, view_count) VALUES
('iPhone 12 128G', 2500.00, 'upload/placeholder.jpg', 'Used iPhone 12, battery 89%', 2, 1, 567),
('MacBook Pro M1', 5500.00, 'upload/placeholder.jpg', 'MacBook Pro 13 inch', 2, 2, 432),
('AirPods Pro 2', 850.00, 'upload/placeholder.jpg', 'AirPods Pro 2nd gen', 2, 3, 312),
('iPad Air 5', 3200.00, 'upload/placeholder.jpg', 'iPad Air 5 256GB', 2, 4, 289),
('Xiaomi 13 Pro', 3800.00, 'upload/placeholder.jpg', 'Xiaomi 13 Pro 8+256GB', 2, 5, 412),
('Apple Watch S8', 1800.00, 'upload/placeholder.jpg', 'Apple Watch Series 8', 2, 1, 198),
('Mechanical Keyboard', 350.00, 'upload/placeholder.jpg', 'RGB backlight keyboard', 2, 2, 156),
('Logitech Mouse', 450.00, 'upload/placeholder.jpg', 'Wireless ergonomic mouse', 2, 3, 234),
('Samsung T7 SSD', 480.00, 'upload/placeholder.jpg', '500GB portable SSD', 2, 4, 167),
('Sony Headphones', 1500.00, 'upload/placeholder.jpg', 'WH-1000XM4 noise cancelling', 2, 5, 278);

-- Category 3: Daily Items
INSERT INTO t_product (name, price, image_url, description, category_id, user_id, view_count) VALUES
('Bookshelf', 80.00, 'upload/placeholder.jpg', 'White bookshelf, adjustable', 3, 1, 123),
('Desk Lamp', 99.00, 'upload/placeholder.jpg', 'Eye-care desk lamp', 3, 2, 201),
('Thermos Cup', 35.00, 'upload/placeholder.jpg', 'Stainless steel thermos', 3, 3, 89),
('Storage Boxes', 45.00, 'upload/placeholder.jpg', 'Set of 3 storage boxes', 3, 4, 156),
('Bed Desk', 65.00, 'upload/placeholder.jpg', 'Foldable bed desk', 3, 5, 178),
('Neck Pillow', 25.00, 'upload/placeholder.jpg', 'Memory foam neck pillow', 3, 1, 234),
('Umbrella', 40.00, 'upload/placeholder.jpg', 'Auto open/close umbrella', 3, 2, 98),
('Clothes Hangers', 20.00, 'upload/placeholder.jpg', 'Set of 20 hangers', 3, 3, 145),
('Slippers', 15.00, 'upload/placeholder.jpg', 'Home slippers', 3, 4, 312),
('Garbage Bags', 10.00, 'upload/placeholder.jpg', 'Heavy duty garbage bags', 3, 5, 267);

-- Category 4: Clothing
INSERT INTO t_product (name, price, image_url, description, category_id, user_id, view_count) VALUES
('Nike Air Force 1', 450.00, 'upload/placeholder.jpg', 'White sneakers size 42', 4, 1, 345),
('Down Jacket', 280.00, 'upload/placeholder.jpg', 'Light down jacket size L', 4, 2, 189),
('Levis Jeans', 180.00, 'upload/placeholder.jpg', 'Blue jeans W32 L32', 4, 3, 223),
('Sports Backpack', 120.00, 'upload/placeholder.jpg', 'Large sports backpack', 4, 4, 156),
('Casio Watch', 550.00, 'upload/placeholder.jpg', 'G-Shock solar watch', 4, 5, 278),
('Wool Scarf', 150.00, 'upload/placeholder.jpg', 'Pure wool scarf', 4, 1, 89),
('Baseball Cap', 65.00, 'upload/placeholder.jpg', 'Adjustable baseball cap', 4, 2, 134),
('Sunglasses', 180.00, 'upload/placeholder.jpg', 'Polarized sunglasses', 4, 3, 198),
('Leather Belt', 95.00, 'upload/placeholder.jpg', 'Genuine leather belt', 4, 4, 76),
('Wallet', 120.00, 'upload/placeholder.jpg', 'Leather wallet', 4, 5, 145);

-- Category 5: Sports
INSERT INTO t_product (name, price, image_url, description, category_id, user_id, view_count) VALUES
('Badminton Racket', 380.00, 'upload/placeholder.jpg', 'Yonex NR-D11', 5, 1, 167),
('Yoga Mat', 45.00, 'upload/placeholder.jpg', 'TPE yoga mat 6mm', 5, 2, 289),
('Dumbbells 20kg', 180.00, 'upload/placeholder.jpg', 'Adjustable dumbbells', 5, 3, 134),
('Basketball', 120.00, 'upload/placeholder.jpg', 'Spalding basketball', 5, 4, 212),
('Jump Rope', 30.00, 'upload/placeholder.jpg', 'Weighted jump rope', 5, 5, 345),
('Wrist Wraps', 25.00, 'upload/placeholder.jpg', 'Sports wrist wraps', 5, 1, 89),
('Sports Bottle', 55.00, 'upload/placeholder.jpg', 'Stainless steel bottle', 5, 2, 178),
('Hiking Backpack', 280.00, 'upload/placeholder.jpg', '40L hiking backpack', 5, 3, 98),
('Knee Support', 65.00, 'upload/placeholder.jpg', 'Sports knee support', 5, 4, 145),
('Running Shoes', 580.00, 'upload/placeholder.jpg', 'Asics Gel-Nimbus', 5, 5, 267);

-- Category 6: Others
INSERT INTO t_product (name, price, image_url, description, category_id, user_id, view_count) VALUES
('Yamaha Guitar', 850.00, 'upload/placeholder.jpg', 'F310 acoustic guitar', 6, 1, 234),
('Succulent Plants', 60.00, 'upload/placeholder.jpg', 'Succulent plant set', 6, 2, 156),
('Postcards', 20.00, 'upload/placeholder.jpg', 'Set of 10 postcards', 6, 3, 78),
('Power Bank', 89.00, 'upload/placeholder.jpg', '20000mAh power bank', 6, 4, 312),
('Vintage Lamp', 75.00, 'upload/placeholder.jpg', 'Vintage style lamp', 6, 5, 189),
('Postcard Album', 35.00, 'upload/placeholder.jpg', 'Postcard collection album', 6, 1, 67),
('Bookmarks', 15.00, 'upload/placeholder.jpg', 'Wooden bookmarks', 6, 2, 123),
('Mouse Pad', 30.00, 'upload/placeholder.jpg', 'Large RGB mouse pad', 6, 3, 245),
('USB Fan', 25.00, 'upload/placeholder.jpg', 'Mini USB fan', 6, 4, 198),
('Notebook', 45.00, 'upload/placeholder.jpg', 'Hardcover notebook', 6, 5, 112);

SELECT 'Products inserted' AS result, COUNT(*) AS total FROM t_product;
SELECT 'Users inserted' AS result, COUNT(*) AS total FROM t_user;