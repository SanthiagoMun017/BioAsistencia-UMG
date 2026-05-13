-- ============================================================
-- BioAsistencia - Sistema de Asistencia de Catedraticos
-- Base de datos MySQL 8.0
-- VII Ciclo - Ingenieria en Sistemas
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ── Tabla: usuarios (administradores y coordinadores) ────────
CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario     INT AUTO_INCREMENT PRIMARY KEY,
    nombre         VARCHAR(100) NOT NULL,
    apellido       VARCHAR(100) NOT NULL,
    correo         VARCHAR(150) NOT NULL UNIQUE,
    contrasena     VARCHAR(255) NOT NULL COMMENT 'bcrypt hash',
    rol            ENUM('admin', 'coordinador') NOT NULL DEFAULT 'coordinador',
    activo         TINYINT(1) NOT NULL DEFAULT 1,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_correo (correo),
    INDEX idx_rol (rol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Tabla: departamentos ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS departamentos (
    id_departamento INT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(150) NOT NULL,
    codigo          VARCHAR(20)  NOT NULL UNIQUE,
    activo          TINYINT(1) NOT NULL DEFAULT 1,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Tabla: catedraticos ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS catedraticos (
    id_catedratico  INT AUTO_INCREMENT PRIMARY KEY,
    codigo          VARCHAR(20)  NOT NULL UNIQUE COMMENT 'Ej: CAT-001',
    nombre          VARCHAR(100) NOT NULL,
    apellido        VARCHAR(100) NOT NULL,
    correo          VARCHAR(150),
    id_departamento INT NOT NULL,
    tipo_contrato   ENUM('tiempo_completo','medio_tiempo','por_hora') NOT NULL DEFAULT 'tiempo_completo',
    horario         VARCHAR(100) COMMENT 'Ej: 07:00 - 13:00',
    turno           ENUM('matutino','vespertino','nocturno') NOT NULL DEFAULT 'matutino',
    cursos_asignados INT NOT NULL DEFAULT 0,
    activo          TINYINT(1) NOT NULL DEFAULT 1,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (id_departamento) REFERENCES departamentos(id_departamento) ON UPDATE CASCADE,
    INDEX idx_codigo (codigo),
    INDEX idx_departamento (id_departamento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Tabla: huellas_digitales ─────────────────────────────────
CREATE TABLE IF NOT EXISTS huellas_digitales (
    id_huella       INT AUTO_INCREMENT PRIMARY KEY,
    id_catedratico  INT NOT NULL,
    template_data   LONGBLOB NOT NULL COMMENT 'Plantilla biometrica del sensor',
    sensor_slot     SMALLINT NOT NULL COMMENT 'Slot en la memoria del sensor Arduino (1-127)',
    id_sensor       INT,
    fecha_registro  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activa          TINYINT(1) NOT NULL DEFAULT 1,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_catedratico) REFERENCES catedraticos(id_catedratico) ON DELETE CASCADE,
    UNIQUE KEY uk_catedratico_activa (id_catedratico, activa),
    INDEX idx_sensor_slot (sensor_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Tabla: sensores ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sensores (
    id_sensor       INT AUTO_INCREMENT PRIMARY KEY,
    codigo          VARCHAR(50) NOT NULL UNIQUE,
    modelo          VARCHAR(100) NOT NULL COMMENT 'Ej: R305, AS608',
    ip_address      VARCHAR(45),
    ubicacion       VARCHAR(100),
    token_dispositivo VARCHAR(64) NOT NULL UNIQUE COMMENT 'Token para autenticacion del Arduino',
    estado          ENUM('activo','inactivo','error') NOT NULL DEFAULT 'activo',
    ultima_conexion DATETIME,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Tabla: asistencias ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS asistencias (
    id_asistencia   INT AUTO_INCREMENT PRIMARY KEY,
    id_catedratico  INT NOT NULL,
    id_sensor       INT,
    fecha_hora      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha           DATE GENERATED ALWAYS AS (DATE(fecha_hora)) STORED,
    estado          ENUM('presente','ausente','tardanza') NOT NULL DEFAULT 'presente',
    metodo_registro ENUM('biometrico','manual') NOT NULL DEFAULT 'biometrico',
    observacion     VARCHAR(255),
    registrado_por  INT COMMENT 'FK a usuarios - si fue manual',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_catedratico) REFERENCES catedraticos(id_catedratico) ON UPDATE CASCADE,
    FOREIGN KEY (id_sensor) REFERENCES sensores(id_sensor) ON DELETE SET NULL,
    FOREIGN KEY (registrado_por) REFERENCES usuarios(id_usuario) ON DELETE SET NULL,
    INDEX idx_catedratico_fecha (id_catedratico, fecha),
    INDEX idx_fecha (fecha),
    INDEX idx_estado (estado),
    UNIQUE KEY uk_cat_fecha (id_catedratico, fecha) COMMENT 'Un registro por catedratico por dia'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Tabla: sensor_eventos ────────────────────────────────────
-- Registros crudos que llegan del Arduino antes de procesarse
CREATE TABLE IF NOT EXISTS sensor_eventos (
    id_evento       INT AUTO_INCREMENT PRIMARY KEY,
    id_sensor       INT NOT NULL,
    sensor_slot     SMALLINT NOT NULL COMMENT 'Slot de la huella reconocida',
    id_catedratico  INT COMMENT 'Resuelto despues de lookup',
    timestamp_sensor BIGINT NOT NULL COMMENT 'Unix timestamp del Arduino',
    procesado       TINYINT(1) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_sensor) REFERENCES sensores(id_sensor),
    INDEX idx_procesado (procesado),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Tabla: sesiones_jwt ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS sesiones_jwt (
    id_sesion   INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario  INT NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  DATETIME NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    INDEX idx_token (token_hash),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- DATOS INICIALES
-- ============================================================

-- Departamentos
INSERT INTO departamentos (nombre, codigo) VALUES
('Ingenieria en Sistemas',          'SIS'),
('Matematicas',                     'MAT'),
('Fisica',                          'FIS'),
('Idiomas',                         'IDI'),
('Quimica',                         'QUI'),
('Ciencias Sociales',               'SOC');

-- Usuarios (contrasena: admin1234 en bcrypt)
INSERT INTO usuarios (nombre, apellido, correo, contrasena, rol) VALUES
('Administrador', 'Sistema',  'admin@bioasistencia.edu',       '$2y$10$X2KmYY/MpDCANnkdAMM8X.ivErqGqSP3pLp.0BiUnGlsEBjPxu6kS', 'admin'),
('Juan',          'Mendez',   'coordinador@bioasistencia.edu', '$2y$10$X2KmYY/MpDCANnkdAMM8X.ivErqGqSP3pLp.0BiUnGlsEBjPxu6kS', 'coordinador');

-- Sensor Arduino
INSERT INTO sensores (codigo, modelo, ip_address, ubicacion, token_dispositivo, estado) VALUES
('SENSOR-001', 'AS608', '192.168.1.50', 'Edificio Principal - Entrada', 'tok_sensor_bioasistencia_2026_secret_abc123xyz', 'activo');

-- Catedraticos de ejemplo
INSERT INTO catedraticos (codigo, nombre, apellido, correo, id_departamento, tipo_contrato, horario, turno, cursos_asignados) VALUES
('CAT-001', 'Carlos',  'Mendoza Perez',   'cmendoza@uni.edu',   1, 'tiempo_completo', '07:00 - 13:00', 'matutino',    3),
('CAT-002', 'Maria',   'Fuentes Lopez',   'mfuentes@uni.edu',   2, 'tiempo_completo', '07:00 - 13:00', 'matutino',    2),
('CAT-003', 'Roberto', 'Paz Gomez',       'rpaz@uni.edu',       3, 'medio_tiempo',    '08:00 - 12:00', 'matutino',    2),
('CAT-004', 'Ana',     'Lopez Cifuentes', 'alopez@uni.edu',     4, 'tiempo_completo', '07:00 - 13:00', 'matutino',    4),
('CAT-005', 'Pedro',   'Ruiz Morales',    'pruiz@uni.edu',      1, 'tiempo_completo', '07:00 - 13:00', 'matutino',    3),
('CAT-006', 'Sandra',  'Ruiz Vega',       'sruiz@uni.edu',      4, 'medio_tiempo',    '14:00 - 18:00', 'vespertino',  2),
('CAT-007', 'Marco',   'Vega Castillo',   'mvega@uni.edu',      3, 'por_hora',        '09:00 - 11:00', 'matutino',    1),
('CAT-008', 'Juan',    'Gomez Herrera',   'jgomez@uni.edu',     2, 'tiempo_completo', '07:00 - 13:00', 'matutino',    3);
