# NotificationHub

跨平台跨品牌个人设备通知流转系统。

## 快速启动

`ash
cd notification-hub/server
run.bat
`

启动后访问 https://localhost:8856，登录 admin/admin123

## 项目结构

server/ - Python FastAPI 服务端
android/ - Android Studio 项目

## API

| Method | Path | 说明 |
|--------|------|------|
| POST | /api/auth/login | Web登录 |
| POST | /api/auth/register | 设备注册 |
| GET | /api/system/status | 状态 |
