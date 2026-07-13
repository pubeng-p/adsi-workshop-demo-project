CREATE TABLE memo_edit_history (
    id UUID PRIMARY KEY,
    attendance_record_id UUID NOT NULL REFERENCES attendance_records(id),
    editor_id UUID NOT NULL REFERENCES employees(id),
    old_memo VARCHAR(200),
    new_memo VARCHAR(200),
    edited_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_memo_edit_history_record ON memo_edit_history(attendance_record_id);
CREATE INDEX idx_memo_edit_history_editor ON memo_edit_history(editor_id);
