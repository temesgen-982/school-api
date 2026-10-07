CREATE TABLE schools (
    id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT pk_schools PRIMARY KEY (id),
    CONSTRAINT uk_schools_code UNIQUE (code),
    CONSTRAINT ck_schools_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
