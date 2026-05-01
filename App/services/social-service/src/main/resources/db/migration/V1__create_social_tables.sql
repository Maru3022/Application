-- Social Service Flyway Migration
CREATE TABLE challenges (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id   UUID NOT NULL,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    type         VARCHAR(30) NOT NULL,
    start_date   DATE NOT NULL,
    end_date     DATE NOT NULL,
    target_value INT,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE challenge_participants (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL,
    progress     INT DEFAULT 0,
    UNIQUE(challenge_id, user_id)
);

CREATE TABLE posts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    content     TEXT NOT NULL,
    type        VARCHAR(30),
    likes_count INT DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE post_likes (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    UNIQUE(post_id, user_id)
);

CREATE TABLE friendships (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    friend_id  UUID NOT NULL,
    status     VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, friend_id)
);
