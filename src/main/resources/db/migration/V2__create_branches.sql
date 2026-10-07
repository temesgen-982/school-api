CREATE TABLE branches (
    id UUID NOT NULL,
    school_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100) NOT NULL,
    is_primary BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    address_line VARCHAR(255),
    city VARCHAR(100),
    region VARCHAR(100),
    phone VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT pk_branches PRIMARY KEY (id),
    CONSTRAINT uk_branches_school_code UNIQUE (school_id, code),
    CONSTRAINT ck_branches_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_branches_school FOREIGN KEY (school_id) REFERENCES schools (id)
);

CREATE INDEX idx_branches_school_id ON branches (school_id);
