-- Set admin login password to 123456 (BCrypt).
-- Target DB: smart_edu_g12 (or your local RuoYi schema).

UPDATE sys_user
SET password = '$2a$10$J6vOCU1f/wkgFsvPtiCUsuLFKdN06aTfZwhryID0YF5BfnGaY4JeO',
    pwd_update_date = NOW()
WHERE user_name = 'admin';
