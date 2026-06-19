# 第一阶段个人工作总结报告（成员 6）

**课程**：分布式软件原理与技术  
**项目名称**：校园二手交易平台  
**本人角色**：成员 6 — Docker 部署与整合协调  
**报告说明**：本文仅总结**本人分工范围内**的工作、问题与体会，不代表其他成员模块的实现细节。

---

## 1. 本人承担任务

依据《团队分工方案》**成员 6：Docker 部署 + 整合协调**，第一阶段要求如下：

| 序号 | 任务 | 验收要点 |
|:---:|:---|:---|
| ① | 编写 **Dockerfile**，将项目打包为可运行镜像 | 镜像内可启动 Web 应用 |
| ② | 编写 **docker-compose.yml**，一键启动 **MySQL、Redis、两个 Tomcat**（方案中双实例为 8080 / 8081） | `docker compose up -d` 后各服务正常 |
| ③ | 编写 **Windows / Linux** 启动脚本 | 一条命令或一键脚本拉起环境 |
| ④ | **代码合并与协调** | 各分支合并、冲突处理、联调口径统一 |
| ⑤ | 更新 **README / 快速启动** 类文档 | 他人可按文档独立复现 |

实际执行中，为与现有功能（**MinIO、Redis Session**）一致，Compose 中**一并编排了 MinIO 服务**，避免双 Tomcat 实例在演示图片上传时出现对象存储未就绪的问题。

---

## 2. 本人完成的具体工作

### 2.1 Dockerfile

- 采用**多阶段构建**：构建阶段使用 **`maven:3.9-eclipse-temurin-8`** 执行 **`mvn package`**；运行阶段使用 **`tomcat:9.0-jre8-temurin`** 部署 **`ROOT.war`**。  
- 选用 **Tomcat 9** 的原因：联调中发现 **Tomcat 7 + Spring 5.3** 在提供 **`/upload/**`** 等静态资源时会触发 **`setContentLengthLong`** 相关 **`NoSuchMethodError`**（Servlet 版本过旧），与分工中「双实例可演示」目标冲突，故在容器侧统一到 **Servlet 3.1+** 运行时。  
- 构建时将 **`docker/campus-db.properties`**、**`docker/campus-redis-config.properties`** 覆盖写入 WAR 内 **`WEB-INF/classes`**，使容器内 JDBC、Redis、MinIO 主机名指向 Compose 服务名（**`mysql` / `redis` / `minio`**），与宿主机 **`localhost`** 开发配置解耦。  
- 运行镜像中设置 **`LANG=C.UTF-8`**，并在 **`CATALINA_OPTS`** 中增加 **`-Dfile.encoding=UTF-8`**，降低容器默认区域带来的编码风险。

### 2.2 docker-compose.yml

- 服务：**MySQL 8**、**Redis 7**、**MinIO**、**Tomcat ×2**（映射宿主 **8080**、**8081**）。  
- 使用 Compose 顶层 **`name: campus-secondhand`** 固定项目名；**去掉各服务的固定 `container_name`**，避免与本机或其它项目残留容器**重名冲突**。  
- MySQL 数据初始化：挂载 **`docker/init/01-schema.sql`** 至 **`/docker-entrypoint-initdb.d`**；脚本中增加 **`SET NAMES utf8mb4`**，并在 **`docker/campus-db.properties`** 的 JDBC URL 中对齐 **`UTF-8` + `utf8mb4_unicode_ci`**，解决本人联调时遇到的**分类中文乱码**问题。  
- Redis 宿主端口映射为 **`6380:6379`**：本人环境中本机 **6379** 已被占用，若强行绑定会导致 Compose 启动失败；**容器内应用仍访问 `redis:6379`**，仅改变宿主机调试端口。  
- MySQL 增加 **`LANG` / `LC_ALL`** 环境变量，与 Dockerfile 策略一致。  
- 两个 Tomcat 挂载**同一上传数据卷**至 **`/data/upload/`**，与 WAR 内 **`upload.path`** 一致，便于**本地上传回退路径**在双实例间共享。

### 2.3 启动脚本与辅助文件

- **`start.bat`**（Windows）、**`start.sh`**（Linux/macOS）：封装 **`docker compose up -d --build`**，并打印访问地址与常用端口说明。  
- **`.dockerignore`**：排除 **`target/`**、**`.git`** 等，缩短构建上下文、加快构建。

### 2.4 文档与协作说明

- 更新 **`README.md`**：增加 **Docker 一键启动**、端口与密码说明、容器名/端口/中文乱码等**故障排除**，以及建议分支 **`stage1-docker`** 的**合并协调**说明。  
- 更新 **`快速启动指南.md`**：新增完整 **「Docker Compose 一键启动」** 章节，并与传统 **Tomcat / Maven 插件** 方式并列；顺延原章节编号，补充项目结构中 Docker 相关文件说明。

### 2.5 整合联调中配合的修改（非本人模块主责，为保障镜像可构建、可运行）

- **`pom.xml`**：Docker 构建使用 **JDK 8**，原 **`<proc>full</proc>`** 导致 **`javac: invalid flag: -proc:full`**，本人与仓库维护方式协商后**移除该配置**，保证 **`maven:3.9-eclipse-temurin-8`** 内 **`mvn package`** 成功。  
- 上述修改属于**构建工具链与 CI/Docker 一致性**范畴，作为成员 6 在**整体验收路径**上做的必要协调。

---

## 3. 本人遇到的问题与处理

| 序号 | 问题现象 | 原因分析 | 本人采取的处理 |
|:---:|:---|:---|:---|
| 1 | 镜像构建失败 `-proc:full` | JDK 8 不支持该编译参数 | 调整 **`pom.xml`** 编译插件配置 |
| 2 | `container name … already in use` | 历史容器占用固定名称 | 去掉 **`container_name`**，使用 Compose **`name`** |
| 3 | `6379` 端口已被分配 | 与本机 Redis 冲突 | Compose 改为 **`6380:6379`**，并更新文档 |
| 4 | Docker 下分类等中文乱码 | JDBC 与 init 会话未对齐 **utf8mb4** | 更新 **`campus-db.properties`**、**`01-schema.sql`**；必要时 **`docker compose down -v`** 重建数据卷 |
| 5 | 双实例静态资源 / Servlet 版本 | Tomcat 7 过旧 | Dockerfile 固定 **Tomcat 9** |

---

## 4. 本人负责模块的验收方式（自测）

1. 在项目根目录执行 **`docker compose up -d --build`**（或运行 **`start.bat`**）。  
2. 浏览器访问 **http://localhost:8080** 与 **http://localhost:8081**，首页可打开。  
3. 在 **8080** 登录后，访问 **8081** 同一路径，**仍显示已登录**（依赖成员 1 已实现的 **Redis Session**；本人负责提供**双实例可同时运行的 Compose 环境**）。  
4. 分类、商品列表等**中文显示正常**。  
5. 执行 **`docker compose down`** 可正常停止；若需彻底重置数据，使用 **`docker compose down -v`** 后再次启动。

---

## 5. 合并与协调（简述）

- 在 **`README.md`** 中约定：Docker 与整合相关改动优先在分支 **`stage1-docker`** 上开发，合并到 **`dev`** 前需本地 **`mvn clean package`** 与 **Docker 联调**通过。  
- 具体与各成员的 **PR 评审、冲突解决** 在组内会议与 Git 流程中完成；本文不展开他人模块代码细节。

---

## 6. 收获与不足

**收获**：熟悉了 **多阶段 Dockerfile**、**Compose 健康检查与依赖顺序**、**宿主机端口与容器网络的差异**，以及**字符集、Servlet 版本**对「本地能跑、Docker 也能跑」的影响。

**不足**：首次编排时对**固定容器名**、**本机端口占用**考虑不周，依赖联调踩坑后迭代；后续会在初版 Compose 中预留**可配置端口**或写入**更醒目的环境前置检查说明**。

---

## 7. 对第二阶段的个人展望（成员 6）

根据分工方案，第二阶段本人任务侧重**压测对比、演示材料**等。计划在第一阶段 **Docker 多实例环境** 上，配合组内完成**推荐相关接口**的性能对比与答辩演示脚本，具体以第二阶段任务书为准。
