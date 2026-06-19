-- 校园二手交易平台数据库表结构

-- 创建数据库
CREATE DATABASE IF NOT EXISTS secondhand_market DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE secondhand_market;

-- 用户表
CREATE TABLE t_user (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码(MD5加密)',
    nickname VARCHAR(50) COMMENT '昵称(可选,默认使用用户名)',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    role INT DEFAULT 1 COMMENT '角色:1-普通用户,2-管理员',
    status INT DEFAULT 1 COMMENT '状态:1-正常,0-冻结',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


-- 分类表
CREATE TABLE t_category (
    id INT PRIMARY KEY AUTO_INCREMENT,
    category_name VARCHAR(50) NOT NULL UNIQUE COMMENT '分类名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- 商品表
CREATE TABLE t_product (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL COMMENT '商品名称',
    price DECIMAL(10,2) NOT NULL COMMENT '价格',
    image_url VARCHAR(500) COMMENT '商品图片路径',
    description TEXT COMMENT '商品描述',
    category_id INT NOT NULL COMMENT '分类ID',
    user_id INT NOT NULL COMMENT '卖家ID',
    status INT DEFAULT 0 COMMENT '状态：0-在售，1-已售出，2-下架',
    view_count INT DEFAULT 0 COMMENT '浏览量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (category_id) REFERENCES t_category(id),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    INDEX idx_category (category_id),
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 订单表
CREATE TABLE t_order (
    id INT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号（UUID）',
    user_id INT NOT NULL COMMENT '买家ID',
    product_id INT NOT NULL COMMENT '商品ID',
    total_price DECIMAL(10,2) NOT NULL COMMENT '订单总价',
    status INT DEFAULT 0 COMMENT '订单状态：0-待处理，1-已完成，2-已取消',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    FOREIGN KEY (product_id) REFERENCES t_product(id),
    INDEX idx_user (user_id),
    INDEX idx_product (product_id),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 插入初始分类数据
INSERT INTO t_category (category_name) VALUES
('闲置书籍'),
('数码产品'),
('生活用品'),
('服装配饰'),
('运动健身'),
('其他');

-- 用户兴趣画像表（成员A：用户兴趣画像系统）
-- 存储用户的多维兴趣画像数据，以 JSON 格式持久化
-- 日常读写走 Redis，MySQL 作为持久化备份
CREATE TABLE t_user_profile (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL UNIQUE COMMENT '用户ID',
    profile_data JSON COMMENT '画像数据（JSON格式）：包含分类权重、价格区间、关键词等',
    version BIGINT DEFAULT 0 COMMENT '版本号（乐观锁）',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户兴趣画像表';

-- 插入管理员账号（密码：admin123，MD5加密后）
INSERT INTO t_user (username, password, nickname, role) VALUES
('admin', '0192023a7bbd73250516f069df18b500', '系统管理员', 2);



