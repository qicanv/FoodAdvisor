\set ON_ERROR_STOP on

DO $upgrade$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM chat_messages
         WHERE request_id IS NOT NULL
         GROUP BY session_id, request_id, role
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot upgrade: duplicate (session_id, request_id, role) rows exist in chat_messages';
    END IF;
END
$upgrade$;

BEGIN;

DROP INDEX IF EXISTS uk_chat_messages_session_request;

CREATE UNIQUE INDEX uk_chat_messages_session_request
    ON chat_messages(session_id, request_id, role)
    WHERE request_id IS NOT NULL;

COMMIT;
