# 校园二手交易平台

基于SSM框架（Spring + Spring MVC + MyBatis）开发的校园二手交易/跳蚤市场平台。

## 项目简介

本项目是一个完整的B/S架构企业级应用，实现了校园内的二手商品交易功能。系统分为三个角色：买家、卖家和管理员，支持商品浏览、搜索、发布、购买、订单管理等完整功能。

## 技术栈

### 后端
- **Java**: JDK 1.8
- **Spring 5.x**: IOC容器和AOP事务控制
- **Spring MVC 5.x**: Web层框架，处理HTTP请求
- **MyBatis 3.x**: 持久层框架，数据库操作
- **MySQL 8.0**: 关系型数据库
- **Druid**: 数据库连接池
- **PageHelper**: MyBatis分页插件
- **Lombok**: 简化Java Bean代码
- **Apache Commons FileUpload**: 文件上传处理

### 前端
- **JSP**: 视图层技术
- **Bootstrap 4**: 响应式UI框架
- **jQuery**: JavaScript库，简化AJAX操作
- **JSTL**: JSP标准标签库

### 开发工具
- **Maven**: 项目构建和依赖管理
- **Tomcat 8.5+**: Web服务器

## 功能模块

### 1. 用户鉴权中心
- 用户注册（用户名、密码、昵称、联系方式）
- 用户登录（MD5密码加密）
- 个人中心（修改信息、查看我的发布、查看我的订单）
- 登录拦截器（防止未登录访问）

### 2. 商品交易大厅
- 首页商品展示（网格布局，分页显示）
- 热门商品推荐（按浏览量排序）
- 商品搜索与筛选（关键词搜索、分类筛选）
- 商品详情页（展示详细信息、卖家联系方式、浏览量统计）

### 3. 卖家功能区
- 发布商品（名称、价格、描述、分类、图片上传）
- 商品管理（编辑、删除、上架/下架）

### 4. 订单与交易系统
- 立即购买（单件购买，防超卖逻辑）
- 订单生成（事务控制，保证数据一致性）
- 我的订单（买家查看）
- 我的销售（卖家查看）

### 5. 系统管理后台
- 用户管理（查看所有用户、冻结/解冻账号）
- 商品审核（查看所有商品、违规商品下架）
- 订单管理（查看所有订单）
- 数据统计（注册用户数、总交易额、订单总数）

## 数据库设计

### 表结构
- **t_user**: 用户表（id, username, password, nickname, phone, wechat, role, status, create_time）
- **t_category**: 分类表（id, category_name, create_time）
- **t_product**: 商品表（id, name, price, image_url, description, category_id, user_id, status, view_count, create_time）
- **t_order**: 订单表（id, order_no, user_id, product_id, total_price, status, create_time）

## 项目结构

```
src/
├── main/
│   ├── java/
│   │   └── com/campus/
│   │       ├── controller/     # 控制器层
│   │       ├── service/         # 服务层
│   │       ├── dao/            # 数据访问层
│   │       ├── entity/         # 实体类
│   │       ├── util/           # 工具类
│   │       └── interceptor/    # 拦截器
│   ├── resources/
│   │   ├── spring/             # Spring配置文件
│   │   ├── mybatis/            # MyBatis配置
│   │   ├── mapper/             # MyBatis映射文件
│   │   ├── db/                 # 数据库脚本
│   │   └── db.properties       # 数据库配置
│   └── webapp/
│       ├── WEB-INF/
│       │   ├── views/          # JSP页面
│       │   └── web.xml        # Web配置
│       └── index.jsp          # 首页
└── pom.xml                     # Maven配置
```

## 安装部署

### 1. 环境要求
- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+ 或 8.0+
- Tomcat 8.5+（或使用上文 Docker 中的 Tomcat 9 镜像）

### 2. 数据库配置
1. 创建数据库：
```sql
CREATE DATABASE secondhand_market DEFAULT CHARACTER SET utf8mb4;
```

2. 执行SQL脚本：
```bash
mysql -u root -p secondhand_market < src/main/resources/db/schema.sql
```

3. 修改数据库配置（`src/main/resources/db.properties`）：
```properties
jdbc.url=jdbc:mysql://localhost:3306/secondhand_market?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
jdbc.username=your_username
jdbc.password=your_password
```

### 3. 文件上传路径配置
修改 `src/main/resources/db.properties` 中的上传路径：
```properties
upload.path=D:/upload/
```
**注意**：请根据实际系统修改路径，并确保该目录存在且有写权限。

### 4. 编译打包
```bash
mvn clean package
```

### 5. 部署运行
1. 将生成的 `target/secondhand-market.war` 复制到 Tomcat 的 `webapps` 目录
2. 启动 Tomcat
3. 访问 `http://localhost:8080/`

### 6. 默认管理员账号
- 用户名：`admin`
- 密码：`admin123`

## 技术创新点

1. **图片存储分离**：图片存储在服务器文件系统，数据库中只存储路径，提高性能
2. **Spring声明式事务**：使用`@Transactional`注解保证订单创建和商品状态更新的原子性
3. **防超卖逻辑**：在订单创建时检查商品状态，防止并发问题
4. **热门商品推荐算法**：按浏览量排序，实现简单的推荐功能
5. **RESTful风格设计**：URL设计规范，便于维护和扩展
6. **分页查询**：使用PageHelper实现高效的分页功能

## 开发说明

### Git 分支与合并协调（成员6）

第一阶段 Docker 与整合相关改动建议使用分支 **`stage1-docker`**（见《团队分工方案》「Git协作规范」）。各成员按模块分支开发，合并到 `dev` 前在本地执行 `mvn clean package` 与必要的联调；与数据库脚本、Compose 端口冲突时以 **`docker-compose.yml` / `docker/init`** 与 README 本节为准。

### 代码规范
- 使用Lombok简化实体类代码
- 统一异常处理
- 使用MD5加密存储密码
- 使用拦截器实现权限控制

### 注意事项
1. 文件上传路径需要根据实际环境配置
2. 数据库连接信息需要正确配置
3. 确保MySQL时区设置正确
4. 图片上传需要确保目录有写权限

## 作者

校园二手交易平台开发团队

## 许可证

本项目仅用于学习交流使用



