CREATE TABLE demo_user (
  id CHAR(36) PRIMARY KEY,
  token_hash CHAR(64) NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE template (
  id VARCHAR(64) PRIMARY KEY,
  title VARCHAR(120) NOT NULL,
  category VARCHAR(64) NOT NULL,
  description TEXT NOT NULL,
  cover_path VARCHAR(255) NOT NULL,
  required_image_count TINYINT UNSIGNED NOT NULL,
  accepted_mime_types JSON NOT NULL,
  max_file_size_bytes INT UNSIGNED NOT NULL,
  layout_key VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  INDEX idx_template_category (category, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE asset (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  template_id VARCHAR(64) NOT NULL,
  stored_path VARCHAR(255) NOT NULL,
  mime_type VARCHAR(40) NOT NULL,
  size_bytes INT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_asset_user FOREIGN KEY (user_id) REFERENCES demo_user(id),
  CONSTRAINT fk_asset_template FOREIGN KEY (template_id) REFERENCES template(id),
  INDEX idx_asset_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE production_task (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  template_id VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  current_stage VARCHAR(24) NOT NULL,
  result_path VARCHAR(255) NULL,
  failure_reason VARCHAR(500) NULL,
  fail_stage VARCHAR(24) NOT NULL DEFAULT 'NONE',
  step_delay_ms INT UNSIGNED NOT NULL DEFAULT 1500,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  started_at DATETIME(6) NULL,
  finished_at DATETIME(6) NULL,
  CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES demo_user(id),
  CONSTRAINT fk_task_template FOREIGN KEY (template_id) REFERENCES template(id),
  INDEX idx_task_user_history (user_id, created_at DESC, id),
  INDEX idx_task_queue (status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_asset (
  task_id CHAR(36) NOT NULL,
  asset_id CHAR(36) NOT NULL,
  position TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (task_id, position),
  UNIQUE KEY uq_task_asset (task_id, asset_id),
  CONSTRAINT fk_task_asset_task FOREIGN KEY (task_id) REFERENCES production_task(id),
  CONSTRAINT fk_task_asset_asset FOREIGN KEY (asset_id) REFERENCES asset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_stage_event (
  task_id CHAR(36) NOT NULL,
  stage VARCHAR(24) NOT NULL,
  state VARCHAR(24) NOT NULL,
  started_at DATETIME(6) NOT NULL,
  finished_at DATETIME(6) NULL,
  failure_reason VARCHAR(500) NULL,
  PRIMARY KEY (task_id, stage),
  CONSTRAINT fk_stage_task FOREIGN KEY (task_id) REFERENCES production_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
