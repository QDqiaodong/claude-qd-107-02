SET NAMES utf8mb4;

DROP TABLE IF EXISTS room_check;
DROP TABLE IF EXISTS appraisal;
DROP TABLE IF EXISTS retrieval;
DROP TABLE IF EXISTS archive;
DROP TABLE IF EXISTS room;

CREATE TABLE room (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  capacity INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  -- 本库自己的温湿度保管区间（上下限），抄表越不越限按它判
  temp_min DOUBLE NOT NULL DEFAULT 14,
  temp_max DOUBLE NOT NULL DEFAULT 24,
  humidity_min DOUBLE NOT NULL DEFAULT 45,
  humidity_max DOUBLE NOT NULL DEFAULT 60,
  -- 封库口径：连续两班最新抄表都越本库上下限 → 封库；
  -- 只能由抄表驱动，下一班抄表回到区间内才回温解封，页面和目录都改不动它
  sealed BIT NOT NULL DEFAULT 0,
  sealed_date DATE NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE archive (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  title VARCHAR(128) NOT NULL,
  archive_year INT NOT NULL,
  keep_years INT NOT NULL,
  room_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_archive_code (code),
  KEY idx_archive_room (room_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retrieval (
  id BIGINT NOT NULL AUTO_INCREMENT,
  archive_id BIGINT NOT NULL,
  visitor VARCHAR(32) NOT NULL,
  dept VARCHAR(64) NOT NULL,
  retrieve_date DATE NOT NULL,
  due_date DATE NOT NULL,
  return_date DATE NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_retrieval_archive (archive_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE room_check (
  id BIGINT NOT NULL AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  check_date DATE NOT NULL,
  temperature DOUBLE NOT NULL,
  humidity DOUBLE NOT NULL,
  result VARCHAR(16) NOT NULL,
  issue_desc VARCHAR(255) NULL,
  checker VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_check_room_date (room_id, check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 到期鉴定台：到期卷先过鉴定会签，会签齐了才动销毁或续存。
-- open_key 是生成列：未结案单子存档案 id，结案后为 NULL；
-- 靠它给「一张卷只能有一条未结案鉴定」再加一道库内唯一保险。
CREATE TABLE appraisal (
  id BIGINT NOT NULL AUTO_INCREMENT,
  archive_id BIGINT NOT NULL,
  opinion VARCHAR(8) NULL,
  appraiser VARCHAR(32) NULL,
  leader VARCHAR(32) NULL,
  extend_years INT NULL,
  status VARCHAR(8) NOT NULL,
  created_date DATE NOT NULL,
  closed_date DATE NULL,
  snapshot_keep_years INT NOT NULL,
  snapshot_status VARCHAR(16) NOT NULL,
  open_key BIGINT GENERATED ALWAYS AS (CASE WHEN status = '未结案' THEN archive_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_appraisal_open (open_key),
  KEY idx_appraisal_archive (archive_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO room (code, name, capacity, status, temp_min, temp_max, humidity_min, humidity_max, sealed, sealed_date) VALUES
('R-01', '一号库房', 100, '在用', 14, 24, 45, 60, 0, NULL),
-- 二号库 09-17、09-18 连续两班抄表都越了本库上下限，按口径封库，等下一班回温
('R-02', '二号库房', 60, '在用', 14, 24, 45, 60, 1, '2026-09-18'),
('R-03', '三号库房', 40, '整理', 14, 24, 45, 60, 0, NULL),
('R-04', '四号库房', 20, '停用', 14, 24, 45, 60, 0, NULL);

INSERT INTO archive (code, title, archive_year, keep_years, room_id, status) VALUES
('D-2021-001', '干部人事任免材料', 2021, 30, 1, '已借出'),
('D-2022-015', '年度财务决算报表', 2022, 10, 1, '在库'),
('D-2023-088', '基建工程竣工验收资料', 2023, 30, 2, '在库'),
('D-2020-007', '职工工伤认定材料', 2020, 10, 2, '已借出'),
('D-2019-033', '临时性会议纪要', 2019, 5, 1, '在库'),
('D-2016-021', '到期待销业务卷宗', 2016, 10, 2, '在库'),
('D-2015-009', '到期借出业务卷宗', 2015, 10, 1, '已借出'),
('D-2018-002', '已报废设备处置材料', 2018, 5, 2, '已销毁');

INSERT INTO retrieval (archive_id, visitor, dept, retrieve_date, due_date, return_date, status) VALUES
(1, '张建国', '市规划局', '2026-09-08', '2026-09-10', NULL, '调阅中'),
(4, '刘敏', '市人社局', '2026-09-15', '2026-09-20', NULL, '调阅中'),
(7, '赵磊', '市档案局', '2026-09-16', '2026-09-19', NULL, '调阅中'),
(2, '王强', '市审计局', '2026-09-01', '2026-09-05', '2026-09-04', '已归还');

-- D-2019-033（archive id=5）已经开过鉴定单，会签还没齐
INSERT INTO appraisal (archive_id, opinion, appraiser, leader, extend_years, status, created_date, closed_date, snapshot_keep_years, snapshot_status) VALUES
(5, NULL, NULL, NULL, NULL, '未结案', '2026-09-17', NULL, 5, '在库');

INSERT INTO room_check (room_id, check_date, temperature, humidity, result, issue_desc, checker) VALUES
(1, '2026-09-16', 22.0, 52, '正常', NULL, '李保管'),
(2, '2026-09-16', 26.5, 58, '异常', '二楼空调故障，温度偏高', '王保管'),
-- 二号库连续两班越本库上下限：09-17、09-18 两班都越限，按口径封库
(2, '2026-09-17', 26.8, 59, '异常', '空调未修好，温度仍越上限', '王保管'),
(2, '2026-09-18', 27.2, 61, '异常', '温度湿度都越限，二号库封库', '王保管'),
(1, '2026-09-15', 21.5, 51, '正常', NULL, '李保管'),
(3, '2026-09-10', 22.5, 55, '正常', NULL, '李保管');
