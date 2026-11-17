-- Chạy bằng tài khoản quản trị PostgreSQL trên cả hai máy.
-- Nếu role hoặc database đã tồn tại, bỏ qua câu lệnh tương ứng.

CREATE ROLE rmi_user WITH LOGIN PASSWORD 'CHANGE_ME';
CREATE DATABASE rmi_student OWNER rmi_user ENCODING 'UTF8';
