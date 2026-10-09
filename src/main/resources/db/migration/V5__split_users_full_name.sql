ALTER TABLE users ADD COLUMN first_name VARCHAR(100);
ALTER TABLE users ADD COLUMN middle_name VARCHAR(100);
ALTER TABLE users ADD COLUMN last_name VARCHAR(100);

UPDATE users
SET first_name  = (name_parts)[1],
    middle_name = CASE
        WHEN array_length(name_parts, 1) > 2
        THEN array_to_string(name_parts[2 : array_length(name_parts, 1) - 1], ' ')
        ELSE NULL
    END,
    last_name   = CASE
        WHEN array_length(name_parts, 1) > 1
        THEN name_parts[array_length(name_parts, 1)]
        ELSE NULL
    END
FROM (
    SELECT id, regexp_split_to_array(trim(full_name), '\s+') AS name_parts
    FROM users
    WHERE full_name IS NOT NULL AND trim(full_name) <> ''
) AS split
WHERE users.id = split.id;

ALTER TABLE users DROP COLUMN full_name;
