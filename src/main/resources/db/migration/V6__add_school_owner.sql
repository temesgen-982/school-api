ALTER TABLE schools
    ADD COLUMN owner_id UUID;

ALTER TABLE schools
    ADD CONSTRAINT fk_schools_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_schools_owner_id ON schools (owner_id);

-- Append-only trail of ownership transfers: who owned before, who owns now, who executed it
CREATE TABLE school_ownership_history (
    id UUID NOT NULL,
    school_id UUID NOT NULL,
    previous_owner_id UUID,
    owner_id UUID NOT NULL,
    transferred_by UUID,
    transferred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_school_ownership_history PRIMARY KEY (id),
    CONSTRAINT fk_soh_school FOREIGN KEY (school_id) REFERENCES schools (id),
    CONSTRAINT fk_soh_previous_owner FOREIGN KEY (previous_owner_id) REFERENCES users (id),
    CONSTRAINT fk_soh_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_soh_school_id ON school_ownership_history (school_id);
