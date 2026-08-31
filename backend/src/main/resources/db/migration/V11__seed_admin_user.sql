-- =====================================================================
-- V11  Seed a bootstrap ADMIN user.
--      username: admin   password: admin123   (bcrypt, cost 10)
--      CHANGE THIS PASSWORD immediately in any real environment.
-- =====================================================================

insert into users (username, full_name, email, password_hash, status, must_change_password)
values ('admin', 'System Administrator', 'admin@sasimilkservice.local',
        '$2y$10$6eMN1WQVfBr63ley7eT2..XfdJ4k81.6wDFZAiVFXte.WwzoJD2zK',
        'ACTIVE', true);

insert into user_roles (user_id, role_id)
select u.id, r.id
from users u, roles r
where u.username = 'admin' and r.code = 'ADMIN';
