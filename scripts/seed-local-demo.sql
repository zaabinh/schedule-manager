-- Local-only demo data for Schedule Manager.
-- Safe to rerun: stable IDs and natural-key upserts prevent duplicates.
-- Prerequisite: Flyway migrations are applied and at least one ACTIVE Admin exists.
-- Demo teacher password: Demo@123

BEGIN;

DO $$
DECLARE
    v_admin uuid;
    v_department_office uuid;
    v_department_math uuid;
    v_department_literature uuid;
    v_role_teacher uuid;
    v_role_homeroom uuid;
    v_teacher_1 uuid;
    v_teacher_2 uuid;
    v_teacher_3 uuid;
    v_teacher_4 uuid;
    v_teacher_5 uuid;
    v_academic_year uuid;
    v_class_1 uuid;
    v_class_2 uuid;
    v_week_1 uuid;
    v_week_2 uuid;
    v_plan_1 uuid;
    v_plan_2 uuid;
    v_section uuid;
    v_monday date := current_date - (extract(isodow FROM current_date)::integer - 1);
    v_year_name varchar(20);
    v_password_hash varchar(255) := '$2a$10$Wktg8SPELgAaFZpTfdmAqOI7xChrBRVlgax9x7lnRI98h4NIpeoK.';
BEGIN
    SELECT id INTO v_admin
    FROM users
    WHERE system_role = 'ADMIN' AND status = 'ACTIVE'
    ORDER BY created_at, id
    LIMIT 1;

    IF v_admin IS NULL THEN
        RAISE EXCEPTION 'Seed demo requires one ACTIVE Admin. Run the Admin bootstrap first.';
    END IF;

    v_year_name := 'Demo ' || to_char(v_monday, 'YYYY') || '-' || to_char(v_monday + interval '1 year', 'YYYY');

    INSERT INTO departments (id, name, normalized_name, description, is_active)
    VALUES ('10000000-0000-0000-0000-000000000001', 'Văn phòng', 'van phong', 'Hành chính, văn thư và công tác tổng hợp.', true)
    ON CONFLICT (normalized_name) DO UPDATE SET name=EXCLUDED.name, description=EXCLUDED.description, is_active=true, updated_at=now()
    RETURNING id INTO v_department_office;

    INSERT INTO departments (id, name, normalized_name, description, is_active)
    VALUES ('10000000-0000-0000-0000-000000000002', 'Tổ Toán - Tin', 'to toan tin', 'Phụ trách môn Toán, Tin học và hoạt động chuyên môn liên quan.', true)
    ON CONFLICT (normalized_name) DO UPDATE SET name=EXCLUDED.name, description=EXCLUDED.description, is_active=true, updated_at=now()
    RETURNING id INTO v_department_math;

    INSERT INTO departments (id, name, normalized_name, description, is_active)
    VALUES ('10000000-0000-0000-0000-000000000003', 'Tổ Ngữ văn', 'to ngu van', 'Phụ trách môn Ngữ văn và hoạt động giáo dục văn hóa đọc.', true)
    ON CONFLICT (normalized_name) DO UPDATE SET name=EXCLUDED.name, description=EXCLUDED.description, is_active=true, updated_at=now()
    RETURNING id INTO v_department_literature;

    SELECT id INTO STRICT v_role_teacher FROM business_roles WHERE normalized_name='giao vien';
    SELECT id INTO STRICT v_role_homeroom FROM business_roles WHERE normalized_name='giao vien chu nhiem';

    INSERT INTO users (id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_by,approved_at)
    VALUES ('11000000-0000-0000-0000-000000000001','nguyen.an@demo.edu.vn','nguyen.an@demo.edu.vn',v_password_hash,'Nguyễn Văn An','USER','ACTIVE',v_department_math,v_admin,now())
    ON CONFLICT (normalized_email) DO UPDATE SET password_hash=EXCLUDED.password_hash,display_name=EXCLUDED.display_name,status='ACTIVE',department_id=EXCLUDED.department_id,approved_by=v_admin,approved_at=now(),updated_at=now()
    RETURNING id INTO v_teacher_1;

    INSERT INTO users (id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_by,approved_at)
    VALUES ('11000000-0000-0000-0000-000000000002','le.mai@demo.edu.vn','le.mai@demo.edu.vn',v_password_hash,'Lê Thu Mai','USER','ACTIVE',v_department_literature,v_admin,now())
    ON CONFLICT (normalized_email) DO UPDATE SET password_hash=EXCLUDED.password_hash,display_name=EXCLUDED.display_name,status='ACTIVE',department_id=EXCLUDED.department_id,approved_by=v_admin,approved_at=now(),updated_at=now()
    RETURNING id INTO v_teacher_2;

    INSERT INTO users (id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_by,approved_at)
    VALUES ('11000000-0000-0000-0000-000000000003','pham.duc@demo.edu.vn','pham.duc@demo.edu.vn',v_password_hash,'Phạm Minh Đức','USER','ACTIVE',v_department_math,v_admin,now())
    ON CONFLICT (normalized_email) DO UPDATE SET password_hash=EXCLUDED.password_hash,display_name=EXCLUDED.display_name,status='ACTIVE',department_id=EXCLUDED.department_id,approved_by=v_admin,approved_at=now(),updated_at=now()
    RETURNING id INTO v_teacher_3;

    INSERT INTO users (id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_by,approved_at)
    VALUES ('11000000-0000-0000-0000-000000000004','vu.lan@demo.edu.vn','vu.lan@demo.edu.vn',v_password_hash,'Vũ Thanh Lan','USER','ACTIVE',v_department_literature,v_admin,now())
    ON CONFLICT (normalized_email) DO UPDATE SET password_hash=EXCLUDED.password_hash,display_name=EXCLUDED.display_name,status='ACTIVE',department_id=EXCLUDED.department_id,approved_by=v_admin,approved_at=now(),updated_at=now()
    RETURNING id INTO v_teacher_4;

    INSERT INTO users (id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_by,approved_at)
    VALUES ('11000000-0000-0000-0000-000000000005','dang.huy@demo.edu.vn','dang.huy@demo.edu.vn',v_password_hash,'Đặng Quốc Huy','USER','ACTIVE',v_department_office,v_admin,now())
    ON CONFLICT (normalized_email) DO UPDATE SET password_hash=EXCLUDED.password_hash,display_name=EXCLUDED.display_name,status='ACTIVE',department_id=EXCLUDED.department_id,approved_by=v_admin,approved_at=now(),updated_at=now()
    RETURNING id INTO v_teacher_5;

    INSERT INTO user_roles (user_id,business_role_id)
    SELECT teacher_id, role_id FROM (VALUES
        (v_teacher_1,v_role_teacher),(v_teacher_1,v_role_homeroom),
        (v_teacher_2,v_role_teacher),(v_teacher_2,v_role_homeroom),
        (v_teacher_3,v_role_teacher),(v_teacher_4,v_role_teacher),(v_teacher_5,v_role_teacher)
    ) AS roles(teacher_id,role_id)
    ON CONFLICT DO NOTHING;

    INSERT INTO academic_years (id,name,start_date,is_active,created_by)
    VALUES ('20000000-0000-0000-0000-000000000001',v_year_name,v_monday,true,v_admin)
    ON CONFLICT (name) DO UPDATE SET start_date=EXCLUDED.start_date,is_active=true,updated_at=now()
    RETURNING id INTO v_academic_year;

    INSERT INTO school_classes (id,academic_year_id,name,normalized_name,grade,homeroom_teacher_id,is_active)
    VALUES ('30000000-0000-0000-0000-000000000001',v_academic_year,'11A2','11a2',11,v_teacher_1,true)
    ON CONFLICT (academic_year_id,normalized_name) DO UPDATE SET name=EXCLUDED.name,grade=EXCLUDED.grade,homeroom_teacher_id=EXCLUDED.homeroom_teacher_id,is_active=true,updated_at=now()
    RETURNING id INTO v_class_1;

    INSERT INTO school_classes (id,academic_year_id,name,normalized_name,grade,homeroom_teacher_id,is_active)
    VALUES ('30000000-0000-0000-0000-000000000002',v_academic_year,'10A10','10a10',10,v_teacher_2,true)
    ON CONFLICT (academic_year_id,normalized_name) DO UPDATE SET name=EXCLUDED.name,grade=EXCLUDED.grade,homeroom_teacher_id=EXCLUDED.homeroom_teacher_id,is_active=true,updated_at=now()
    RETURNING id INTO v_class_2;

    INSERT INTO school_weeks (id,academic_year_id,sequence_number,display_number,week_type,start_date,end_date)
    VALUES ('40000000-0000-0000-0000-000000000001',v_academic_year,1,1,'STUDY',v_monday,v_monday+6)
    ON CONFLICT (academic_year_id,sequence_number) DO UPDATE SET display_number=1,week_type='STUDY',start_date=EXCLUDED.start_date,end_date=EXCLUDED.end_date,updated_at=now()
    RETURNING id INTO v_week_1;

    INSERT INTO school_weeks (id,academic_year_id,sequence_number,display_number,week_type,start_date,end_date)
    VALUES ('40000000-0000-0000-0000-000000000002',v_academic_year,2,2,'STUDY',v_monday+7,v_monday+13)
    ON CONFLICT (academic_year_id,sequence_number) DO UPDATE SET display_number=2,week_type='STUDY',start_date=EXCLUDED.start_date,end_date=EXCLUDED.end_date,updated_at=now()
    RETURNING id INTO v_week_2;

    INSERT INTO weekly_plans (id,school_week_id,status,morning_duty_class_id,afternoon_duty_class_id,published_at,published_by,created_by)
    VALUES ('50000000-0000-0000-0000-000000000001',v_week_1,'PUBLISHED',v_class_1,v_class_2,now(),v_admin,v_admin)
    ON CONFLICT (school_week_id) DO UPDATE SET status='PUBLISHED',morning_duty_class_id=v_class_1,afternoon_duty_class_id=v_class_2,published_at=now(),published_by=v_admin,updated_at=now()
    RETURNING id INTO v_plan_1;

    INSERT INTO weekly_plans (id,school_week_id,status,morning_duty_class_id,afternoon_duty_class_id,published_at,published_by,created_by)
    VALUES ('50000000-0000-0000-0000-000000000002',v_week_2,'PUBLISHED',v_class_2,v_class_1,now(),v_admin,v_admin)
    ON CONFLICT (school_week_id) DO UPDATE SET status='PUBLISHED',morning_duty_class_id=v_class_2,afternoon_duty_class_id=v_class_1,published_at=now(),published_by=v_admin,updated_at=now()
    RETURNING id INTO v_plan_2;

    FOR v_section IN
        INSERT INTO plan_sections (id,weekly_plan_id,section_type,content,display_order)
        VALUES
          ('60000000-0000-0000-0000-000000000001',v_plan_1,'ACADEMIC_AFFAIRS','Ổn định nền nếp; thực hiện chương trình tuần 1 và dự giờ theo kế hoạch.',1),
          ('60000000-0000-0000-0000-000000000002',v_plan_1,'FACILITIES_OFFICE','Kiểm tra thiết bị phòng học và hoàn thiện báo cáo cơ sở vật chất.',2),
          ('60000000-0000-0000-0000-000000000003',v_plan_1,'YOUTH_UNION','Tổ chức vệ sinh khuôn viên và hoạt động đầu tuần.',3),
          ('60000000-0000-0000-0000-000000000004',v_plan_1,'HOMEROOM_TEACHERS','Rà soát chuyên cần và cập nhật tình hình lớp chủ nhiệm.',4),
          ('60000000-0000-0000-0000-000000000005',v_plan_1,'TEACHERS','Cập nhật sổ đầu bài đầy đủ sau mỗi tiết.',5),
          ('60000000-0000-0000-0000-000000000006',v_plan_2,'ACADEMIC_AFFAIRS','Tiếp tục chương trình tuần 2; hoàn thiện hồ sơ chuyên môn.',1),
          ('60000000-0000-0000-0000-000000000007',v_plan_2,'FACILITIES_OFFICE','Kiểm kê thiết bị dạy học và đề xuất sửa chữa.',2),
          ('60000000-0000-0000-0000-000000000008',v_plan_2,'YOUTH_UNION','Chuẩn bị hoạt động ngoại khóa cho học sinh.',3),
          ('60000000-0000-0000-0000-000000000009',v_plan_2,'HOMEROOM_TEACHERS','Phối hợp phụ huynh đối với học sinh cần hỗ trợ.',4),
          ('60000000-0000-0000-0000-000000000010',v_plan_2,'TEACHERS','Hoàn thành nhập điểm và nhận xét học sinh đúng hạn.',5)
        ON CONFLICT (weekly_plan_id,section_type) DO UPDATE SET content=EXCLUDED.content,display_order=EXCLUDED.display_order,updated_at=now()
        RETURNING id
    LOOP
        INSERT INTO plan_section_targets (id,plan_section_id,target_type)
        VALUES (md5('demo-target-' || v_section::text)::uuid,v_section,'ALL')
        ON CONFLICT DO NOTHING;
    END LOOP;

    INSERT INTO day_sessions (id,weekly_plan_id,session_date,session,base_content)
    SELECT md5(v_plan_1::text || day_date::text || session_name)::uuid,v_plan_1,day_date,session_name,
           CASE WHEN session_name='MORNING' THEN 'Học theo thời khóa biểu' ELSE 'Hoạt động theo kế hoạch tuần' END
    FROM generate_series(v_monday,v_monday+6,interval '1 day') AS day(day_date)
    CROSS JOIN (VALUES ('MORNING'),('AFTERNOON')) AS sessions(session_name)
    ON CONFLICT (weekly_plan_id,session_date,session) DO UPDATE SET base_content=EXCLUDED.base_content,updated_at=now();

    INSERT INTO day_sessions (id,weekly_plan_id,session_date,session,base_content)
    SELECT md5(v_plan_2::text || day_date::text || session_name)::uuid,v_plan_2,day_date,session_name,
           CASE WHEN session_name='MORNING' THEN 'Học theo thời khóa biểu' ELSE 'Hoạt động theo kế hoạch tuần' END
    FROM generate_series(v_monday+7,v_monday+13,interval '1 day') AS day(day_date)
    CROSS JOIN (VALUES ('MORNING'),('AFTERNOON')) AS sessions(session_name)
    ON CONFLICT (weekly_plan_id,session_date,session) DO UPDATE SET base_content=EXCLUDED.base_content,updated_at=now();

    INSERT INTO events (id,weekly_plan_id,content,start_date,end_date,session,start_time,end_time,location,note,created_by)
    VALUES
      ('80000000-0000-0000-0000-000000000001',v_plan_1,'Sinh hoạt chuyên môn toàn trường',current_date,current_date,'MORNING','07:30','09:00','Phòng hội đồng','Dữ liệu demo cho lịch trình hôm nay.',v_admin),
      ('80000000-0000-0000-0000-000000000002',v_plan_2,'Chào cờ đầu tuần',v_monday+7,v_monday+7,'MORNING','07:00','07:45','Sân trường',NULL,v_admin),
      ('80000000-0000-0000-0000-000000000003',v_plan_2,'Họp tổ chuyên môn',v_monday+9,v_monday+9,'AFTERNOON','14:00','15:30','Phòng họp 2','Thống nhất nội dung giảng dạy tuần tiếp theo.',v_admin)
    ON CONFLICT (id) DO UPDATE SET weekly_plan_id=EXCLUDED.weekly_plan_id,content=EXCLUDED.content,start_date=EXCLUDED.start_date,end_date=EXCLUDED.end_date,session=EXCLUDED.session,start_time=EXCLUDED.start_time,end_time=EXCLUDED.end_time,location=EXCLUDED.location,note=EXCLUDED.note,updated_at=now();

    INSERT INTO tasks (id,weekly_plan_id,assignee_user_id,title,description,due_at,status,completed_at,created_by)
    VALUES
      ('90000000-0000-0000-0000-000000000001',v_plan_1,v_teacher_1,'Nộp báo cáo chuyên môn','Tổng hợp tiến độ chương trình của tổ Toán - Tin.',(current_date+1)+time '17:00','TODO',NULL,v_admin),
      ('90000000-0000-0000-0000-000000000002',v_plan_1,v_teacher_2,'Rà soát hồ sơ chủ nhiệm','Cập nhật chuyên cần và tình hình học sinh lớp 10A10.',(current_date+2)+time '16:30','TODO',NULL,v_admin),
      ('90000000-0000-0000-0000-000000000003',v_plan_1,v_teacher_3,'Kiểm tra thiết bị phòng Tin','Ghi nhận thiết bị cần bảo trì hoặc thay thế.',(current_date+3)+time '15:00','TODO',NULL,v_admin),
      ('90000000-0000-0000-0000-000000000004',v_plan_2,v_teacher_4,'Chuẩn bị chuyên đề Ngữ văn','Hoàn thiện nội dung và tài liệu trình chiếu.',(current_date+8)+time '17:00','TODO',NULL,v_admin),
      ('90000000-0000-0000-0000-000000000005',v_plan_2,v_teacher_5,'Tổng hợp đề xuất sửa chữa','Gửi danh sách thiết bị cần sửa chữa cho Ban giám hiệu.',(current_date+9)+time '17:00','TODO',NULL,v_admin)
    ON CONFLICT (id) DO UPDATE SET weekly_plan_id=EXCLUDED.weekly_plan_id,assignee_user_id=EXCLUDED.assignee_user_id,title=EXCLUDED.title,description=EXCLUDED.description,due_at=EXCLUDED.due_at,status='TODO',completed_at=NULL,updated_at=now();

    RAISE NOTICE 'Demo seed completed: 3 departments, 5 teachers, 2 weekly plans, 3 events and 5 tasks.';
    RAISE NOTICE 'Teacher login example: nguyen.an@demo.edu.vn / Demo@123';
END $$;

COMMIT;

SELECT 'departments' AS entity,count(*) AS demo_count FROM departments WHERE normalized_name IN ('van phong','to toan tin','to ngu van')
UNION ALL SELECT 'teachers',count(*) FROM users WHERE normalized_email LIKE '%@demo.edu.vn'
UNION ALL SELECT 'weekly_plans',count(*) FROM weekly_plans WHERE id IN ('50000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000002')
UNION ALL SELECT 'events',count(*) FROM events WHERE id IN ('80000000-0000-0000-0000-000000000001','80000000-0000-0000-0000-000000000002','80000000-0000-0000-0000-000000000003')
UNION ALL SELECT 'tasks',count(*) FROM tasks WHERE id::text LIKE '90000000-0000-0000-0000-00000000000%'
ORDER BY entity;
