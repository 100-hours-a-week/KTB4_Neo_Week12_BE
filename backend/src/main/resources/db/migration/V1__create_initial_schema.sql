CREATE TABLE users (
                       user_id BIGINT NOT NULL AUTO_INCREMENT,
                       email VARCHAR(255) NOT NULL,
                       password VARCHAR(255) NOT NULL,
                       nickname VARCHAR(255) NOT NULL,
                       profile_image VARCHAR(500) NULL,
                       deleted BOOLEAN NOT NULL,
                       deleted_at DATETIME(6) NULL,
                       role ENUM('ROLE_ADMIN', 'ROLE_USER') NULL,
                       created_at DATETIME(6) NOT NULL,
                       updated_at DATETIME(6) NOT NULL,

                       CONSTRAINT pk_users PRIMARY KEY (user_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE posts (
                       post_id BIGINT NOT NULL AUTO_INCREMENT,
                       user_id BIGINT NOT NULL,
                       title VARCHAR(255) NOT NULL,
                       post_body TEXT NOT NULL,
                       post_image VARCHAR(500) NULL,
                       likes INT NOT NULL,
                       views INT NOT NULL,
                       comments INT NOT NULL,
                       edited BOOLEAN NOT NULL,
                       deleted BOOLEAN NOT NULL,
                       blinded BOOLEAN NOT NULL,
                       deleted_at DATETIME(6) NULL,
                       edited_at DATETIME(6) NULL,
                       created_at DATETIME(6) NOT NULL,
                       updated_at DATETIME(6) NOT NULL,

                       CONSTRAINT pk_posts PRIMARY KEY (post_id),
                       CONSTRAINT fk_posts_user
                           FOREIGN KEY (user_id)
                               REFERENCES users (user_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE comments (
                          comment_id BIGINT NOT NULL AUTO_INCREMENT,
                          post_id BIGINT NOT NULL,
                          user_id BIGINT NOT NULL,
                          parent_comment_id BIGINT NULL,
                          comment_body TEXT NOT NULL,
                          edited BOOLEAN NOT NULL,
                          deleted BOOLEAN NOT NULL,
                          deleted_at DATETIME(6) NULL,
                          created_at DATETIME(6) NOT NULL,
                          updated_at DATETIME(6) NOT NULL,

                          CONSTRAINT pk_comments PRIMARY KEY (comment_id),
                          CONSTRAINT fk_comments_post
                              FOREIGN KEY (post_id)
                                  REFERENCES posts (post_id),
                          CONSTRAINT fk_comments_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users (user_id),
                          CONSTRAINT fk_comments_parent
                              FOREIGN KEY (parent_comment_id)
                                  REFERENCES comments (comment_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE refresh_token (
                               id BIGINT NOT NULL AUTO_INCREMENT,
                               user_id BIGINT NOT NULL,
                               token VARCHAR(500) NOT NULL,
                               expiry_date DATETIME(6) NOT NULL,

                               CONSTRAINT pk_refresh_token PRIMARY KEY (id),
                               CONSTRAINT uq_refresh_token_user UNIQUE (user_id),
                               CONSTRAINT uq_refresh_token_token UNIQUE (token),
                               CONSTRAINT fk_refresh_token_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users (user_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE post_likes (
                            post_like_id BIGINT NOT NULL AUTO_INCREMENT,
                            post_id BIGINT NOT NULL,
                            user_id BIGINT NOT NULL,

                            CONSTRAINT pk_post_likes PRIMARY KEY (post_like_id),
                            CONSTRAINT uq_post_likes_post_user
                                UNIQUE (post_id, user_id),
                            CONSTRAINT fk_post_likes_post
                                FOREIGN KEY (post_id)
                                    REFERENCES posts (post_id),
                            CONSTRAINT fk_post_likes_user
                                FOREIGN KEY (user_id)
                                    REFERENCES users (user_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE post_views (
                            post_view_id BIGINT NOT NULL AUTO_INCREMENT,
                            post_id BIGINT NOT NULL,
                            user_id BIGINT NOT NULL,
                            last_viewed_at DATETIME(6) NOT NULL,

                            CONSTRAINT pk_post_views PRIMARY KEY (post_view_id),
                            CONSTRAINT uq_post_views_post_user
                                UNIQUE (post_id, user_id),
                            CONSTRAINT fk_post_views_post
                                FOREIGN KEY (post_id)
                                    REFERENCES posts (post_id),
                            CONSTRAINT fk_post_views_user
                                FOREIGN KEY (user_id)
                                    REFERENCES users (user_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE post_reports (
                              report_id BIGINT NOT NULL AUTO_INCREMENT,
                              post_id BIGINT NOT NULL,
                              user_id BIGINT NOT NULL,
                              report_type ENUM(
        'SPAM',
        'ABUSE',
        'PORNOGRAPHY',
        'FRAUD'
    ) NOT NULL,
                              reason VARCHAR(500) NULL,
                              status ENUM(
        'PENDING',
        'APPROVED',
        'REJECTED'
    ) NOT NULL,
                              reported_at DATETIME(6) NOT NULL,
                              processed_at DATETIME(6) NULL,

                              CONSTRAINT pk_post_reports PRIMARY KEY (report_id),
                              CONSTRAINT uq_post_reports_post_user
                                  UNIQUE (post_id, user_id),
                              CONSTRAINT fk_post_reports_post
                                  FOREIGN KEY (post_id)
                                      REFERENCES posts (post_id),
                              CONSTRAINT fk_post_reports_user
                                  FOREIGN KEY (user_id)
                                      REFERENCES users (user_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE post_edit_history (
                                   history_id BIGINT NOT NULL AUTO_INCREMENT,
                                   post_id BIGINT NOT NULL,
                                   user_id BIGINT NOT NULL,
                                   title VARCHAR(255) NOT NULL,
                                   post_body TEXT NOT NULL,
                                   post_image VARCHAR(500) NULL,
                                   revision_no INT NOT NULL,
                                   created_at DATETIME(6) NOT NULL,

                                   CONSTRAINT pk_post_edit_history PRIMARY KEY (history_id),
                                   CONSTRAINT uq_post_edit_history_post_revision
                                       UNIQUE (post_id, revision_no)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


CREATE TABLE drafts (
                        draft_id BIGINT NOT NULL AUTO_INCREMENT,
                        user_id BIGINT NOT NULL,
                        active_owner_id BIGINT NULL,
                        published_post_id BIGINT NULL,
                        title VARCHAR(255) NULL,
                        post_body TEXT NULL,
                        post_image VARCHAR(500) NULL,
                        status ENUM(
        'ACTIVE',
        'PUBLISHED',
        'DELETED'
    ) NOT NULL,
                        content_version BIGINT NOT NULL,
                        entity_version BIGINT NULL,
                        rdb_saved_at DATETIME(6) NOT NULL,
                        published_at DATETIME(6) NULL,
                        deleted_at DATETIME(6) NULL,
                        created_at DATETIME(6) NOT NULL,
                        updated_at DATETIME(6) NOT NULL,

                        CONSTRAINT pk_drafts PRIMARY KEY (draft_id),
                        CONSTRAINT uk_drafts_active_owner
                            UNIQUE (active_owner_id),
                        CONSTRAINT fk_drafts_user
                            FOREIGN KEY (user_id)
                                REFERENCES users (user_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;