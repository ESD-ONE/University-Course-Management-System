-- One row per scheduled job, held by whichever replica is running it (ShedLock).
-- Plain timestamp, as ShedLock documents: with usingDbTime() it stores the database's UTC
-- clock, which a timestamptz column would reinterpret in each session's time zone.
CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp    NOT NULL,
    locked_at  timestamp    NOT NULL,
    locked_by  varchar(255) NOT NULL
);
