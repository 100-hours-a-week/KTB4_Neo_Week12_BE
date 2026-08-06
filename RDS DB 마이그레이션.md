# H2 DB → AWS RDS MySQL 마이그레이션 계획서

## 1. 문서 목적

EC2 Docker 환경에서 파일 기반으로 운영 중인 H2 데이터베이스를 AWS RDS for MySQL로 이전한다. 데이터베이스 연결 외에 기존 EC2, Redis, S3, CloudFront 및 GitHub Actions 배포 구조는 유지하며, 기존 기능과 API 계약은 변경하지 않는다.

본 계획은 다음 완료 조건을 기준으로 한다.

- 기존 H2의 운영 데이터가 RDS MySQL로 누락 없이 이전된다.
- 기존 API 경로, 요청/응답 형식, 상태 코드와 비즈니스 동작이 유지된다.
- EC2의 Spring Boot 컨테이너만 DB 접속 대상을 RDS로 변경한다.
- Redis, S3, CloudFront 및 프론트엔드 연결 구조는 변경하지 않는다.
- GitHub Actions의 이미지 빌드, Docker Hub 푸시, EC2 자동 배포 흐름을 유지한다.
- 단위·통합 테스트는 AWS 비용 없이 로컬에서 실행할 수 있다.
- MySQL Workbench에서 운영 DB의 스키마와 데이터를 안전하게 확인할 수 있다.
- 장애 발생 시 기존 H2 구성으로 되돌릴 수 있다.

---

## 2. 범위와 경계

### 2.1 작업 범위

- RDS for MySQL 생성 및 네트워크·보안 설정
- Spring Boot 운영 DB 프로필 및 환경변수 구성
- Flyway 기반 MySQL 스키마 관리 활성화
- H2 데이터 추출, 변환, RDS 적재 및 정합성 검증
- 로컬 개발 DB와 자동 테스트 DB를 MySQL 8.4로 통일
- 로컬 Docker MySQL 통합 테스트 환경 유지·정리
- MySQL Workbench의 RDS 접속 경로 구성
- 배포 전후 검증과 롤백 절차 수립

### 2.2 변경하지 않는 범위

- Controller API URL, HTTP method, DTO, 응답 구조
- 회원, 게시글, 댓글, 좋아요, 조회, 신고, 임시저장 및 이미지 업로드 기능
- JWT 인증·인가 방식
- EC2와 Docker 기반 실행 구조
- EC2 내부 Redis와 AOF 볼륨 구조
- S3 Presigned URL, 이미지 검증 및 최종 이미지 URL 저장 방식
- CloudFront 공개 이미지 URL 구조
- GitHub Actions의 `main` 브랜치 기반 Docker 이미지 배포 방식
- 프론트엔드와 Nginx 연결 구조

---

## 3. 현재 구현 상태

저장소의 로컬·테스트·운영 대상 설정은 MySQL 기준으로 전환되었다. 다만 EC2에서 실제로 실행 중인 구버전 컨테이너는 RDS 전환 배포 전까지 기존 H2를 계속 사용할 수 있으므로, 소스 변경 완료와 운영 전환 완료를 구분한다.

| 항목 | 현재 상태 |
|---|---|
| 기본 로컬 DB | Docker MySQL 8.4, `localhost:3307` |
| 자동 테스트 DB | Testcontainers MySQL 8.4 |
| 성능 테스트 DB | Docker MySQL 8.4 |
| 스키마 관리 | 모든 MySQL 환경에서 Flyway V1/V2 적용 |
| Hibernate | `ddl-auto: validate` |
| H2 Dependency/Console/URL | 저장소 설정에서 제거 완료 |
| 운영 프로필 | `application-prod.yaml`, RDS 환경변수 방식 준비 완료 |
| EC2 운영 데이터 | RDS 전환 배포 전까지 기존 H2에 존재 |
| RDS 인스턴스 | AWS Console에서 생성·연결 설정 진행 중 |
| H2 데이터 → RDS 적재 | RDS 생성과 최종 백업 후 수행 예정 |

현재 검증 결과:

- Testcontainers MySQL 8.4 기반 자동 테스트 53개 통과
- Flyway V1/V2 적용 및 Hibernate schema validation 성공
- 운영 Spring Boot JAR 빌드 성공
- Docker Compose 로컬 overlay 문법 검증 성공
- 운영 runtime classpath에서 H2 의존성 제거 확인

---

## 4. 마이그레이션 전 EC2 구조

### 4.1 실행 구조

```text
사용자
  │
  ▼
EC2 :80
  └─ Docker Compose
      ├─ Frontend/Nginx
      ├─ Spring Boot Backend :8080 (외부 비공개)
      │   ├─ H2 File DB: jdbc:h2:file:/app/data/community
      │   └─ AWS SDK → S3
      └─ Redis 7 :6379 (Compose 내부 통신)

S3 이미지 → CloudFront URL → DB에는 이미지 URL만 저장
```

마이그레이션 전 EC2의 운영 H2는 인메모리 DB가 아니다. 구버전 Backend 컨테이너의 `/app/data/community` 파일과 `backend-data` Docker Volume을 사용한다. 최신 저장소의 Compose에서는 H2 mount가 제거됐지만 기존 Docker Volume 자체는 데이터 보호를 위해 자동 삭제하지 않는다.

Redis는 EC2의 별도 컨테이너에서 실행되며 AOF와 `redis-data` Volume을 사용한다. RDS 전환 후에도 이 구성을 유지한다.

### 4.2 관계형 DB 테이블

| 테이블 | 주요 데이터 | 주요 관계·제약 |
|---|---|---|
| `users` | 사용자, 암호화 비밀번호, 프로필 이미지 URL, 권한 | PK `user_id` |
| `posts` | 게시글, 이미지 URL, 좋아요·조회·댓글 집계값 | `users` FK |
| `comments` | 댓글과 대댓글 | `posts`, `users`, 자기참조 FK |
| `refresh_token` | 사용자별 Refresh Token | 사용자 1:1, token UNIQUE |
| `post_likes` | 게시글 좋아요 | `(post_id, user_id)` UNIQUE |
| `post_views` | 사용자별 마지막 조회 시각 | `(post_id, user_id)` UNIQUE |
| `post_reports` | 신고 유형과 처리 상태 | `(post_id, user_id)` UNIQUE |
| `post_edit_history` | 게시글 수정 이력 | `(post_id, revision_no)` UNIQUE |
| `drafts` | 임시저장 스냅샷과 발행 상태 | 활성 사용자 UNIQUE, `users` FK, 낙관적 잠금 버전 |

현재 저장소에는 MySQL 8용 Flyway 파일이 존재한다.

- `V1__create_initial_schema.sql`: 9개 테이블과 기본 제약 생성
- `V2__add_user_post_comment_indexes.sql`: 사용자, 게시글, 댓글 조회 인덱스 생성

단, 현재 H2 스키마는 Hibernate가 생성한 결과이고 MySQL 스키마는 Flyway 정의이므로, 마이그레이션 전에 실제 H2 `INFORMATION_SCHEMA`, JPA 엔티티, Flyway V1/V2 세 가지를 비교해야 한다. 파일이 존재한다는 사실만으로 동일 스키마임을 가정하지 않는다.

### 4.3 테스트와 배포 구조

- 자동 테스트: `application-test.yaml`과 Testcontainers의 일회성 MySQL 8.4 사용
- 로컬 MySQL 검증: `compose.perf.yml`의 MySQL 8.4, Redis, Backend 사용
- MySQL JDBC 드라이버와 Flyway MySQL 모듈은 이미 Gradle Dependency에 포함
- GitHub Actions: Backend 이미지 빌드 → Docker Hub `latest` 및 commit SHA 태그 푸시 → Compose 파일 EC2 복사 → Backend pull/recreate
- 배포 환경값: EC2 배포 디렉터리의 `.env`를 Compose가 읽음

---

## 5. 목표 구조

```text
사용자
  │
  ▼
EC2 Docker Compose
  ├─ Frontend/Nginx                       기존 유지
  ├─ Spring Boot Backend ───────────────┐
  └─ Redis + AOF                         │ 기존 유지
                                         │ MySQL/TLS :3306
                                         ▼
                              RDS for MySQL (Private)

Backend ── Presigned URL/AWS SDK ── S3 ── CloudFront   기존 유지

개발자 PC MySQL Workbench
  └─ SSH Tunnel via EC2 ── RDS Private Endpoint :3306
```

RDS는 Private Subnet 또는 Public Access 비활성 상태를 기본안으로 한다. MySQL Workbench는 RDS를 인터넷에 직접 노출하지 않고 `Standard TCP/IP over SSH` 방식으로 EC2를 Bastion처럼 사용한다.

---

## 6. Dependency와 설정 방침

### 6.1 애플리케이션 Dependency

현재 다음 Dependency를 사용한다.

- `com.mysql:mysql-connector-j`
- `spring-boot-starter-flyway`
- `org.flywaydb:flyway-mysql`
- `spring-boot-starter-data-jpa`
- `org.testcontainers:testcontainers-mysql` 2.0.5

H2 및 H2 Console Dependency는 삭제한다. 자동 테스트에는 Testcontainers MySQL 모듈을 사용하며, Docker에서 일회성 MySQL을 자동 생성하고 종료한다. 테스트는 AWS 리소스를 사용하지 않는다.

### 6.2 프로필 분리

| 프로필 | DB | 용도 | 스키마 정책 |
|---|---|---|---|
| `test` | Testcontainers MySQL 8.4 | 자동 단위·통합 테스트 | `validate`, Flyway on |
| `local` | Docker MySQL 8.4 | 기본 로컬 개발·Flyway·수동 API 검증 | `validate`, Flyway on |
| `prod` | RDS MySQL 8.4 | 운영 | `validate`, Flyway on |

운영 프로필에는 다음 원칙을 적용한다.

- `driver-class-name: com.mysql.cj.jdbc.Driver`
- `ddl-auto: validate`
- Flyway `enabled: true`, `validate-on-migrate: true`
- JDBC URL의 Unicode, UTF-8, timezone, TLS 옵션 명시
- H2 Console과 H2 Driver는 의존성 및 설정에서 제거
- DB 인증정보는 소스와 Docker 이미지에 포함하지 않음
- HikariCP 풀 크기는 EC2 인스턴스 수와 RDS `max_connections`에 맞춰 제한

### 6.3 운영 환경변수

최소 필요 값은 다음과 같다.

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<private-rds-endpoint>:3306/community?...TLS options...
DB_USERNAME=<application-user>
DB_PASSWORD=<secret>
```

이번 전환에서는 확정한 방식대로 EC2 배포 디렉터리의 `.env`에 주입하고 `chmod 600 .env`로 권한을 제한한다. GitHub 저장소, Compose 파일, Docker 이미지, Actions 로그에는 비밀번호를 기록하지 않는다. Secrets Manager/SSM 전환은 후속 보안 개선 범위로 둔다.

---

## 7. 단계별 변경 및 검증 계획

### 단계 0. 기준선 확보와 변경 동결

작업:

1. 마이그레이션 대상 commit SHA와 배포 Docker 이미지 SHA 태그를 기록한다.
2. 현재 운영 API 목록과 대표 요청·응답을 회귀 테스트 기준으로 저장한다.
3. H2 Docker Volume과 DB 파일을 별도 위치에 백업한다.
4. 테이블별 row count, PK 최대값, NULL 수, 주요 집계값을 기록한다.
5. S3/CloudFront URL 샘플과 Redis 임시저장 동작을 기록한다.

검증:

- 백업 H2 파일을 복사본으로 기동해 조회할 수 있어야 한다.
- 현재 `./gradlew test`가 통과해야 한다.
- 회원가입/로그인, 게시글/댓글 CRUD, 좋아요, 조회, 신고, 임시저장/발행, 이미지 업로드의 기준 결과가 확보되어야 한다.

통과 기준: 복구 가능한 H2 백업과 비교 가능한 데이터·API 기준선이 모두 존재한다.

### 단계 1. 실제 H2 스키마 인벤토리 및 MySQL 스키마 확정

선행 조건: 단계 0 완료

작업:

1. H2 `INFORMATION_SCHEMA`에서 테이블, 컬럼, 타입, NULL, PK, FK, UNIQUE, INDEX를 추출한다.
2. 실제 H2 구조와 JPA 엔티티 매핑을 비교한다.
3. 실제 H2 구조와 Flyway V1/V2를 비교한다.
4. MySQL 예약어, `BOOLEAN`, `DATETIME(6)`, `TEXT`, `ENUM`, snake_case 변환을 검토한다.
5. 엔티티가 기대하는 컬럼명과 Flyway 생성 컬럼명이 Hibernate Naming Strategy를 통해 동일하게 해석되는지 검증한다.
6. 스키마 차이가 있으면 API나 엔티티 동작을 바꾸지 않고 Flyway 신규 버전으로 보완한다. 이미 공유된 V1/V2는 배포 후 임의 수정하지 않는다.

검증:

- 빈 로컬 MySQL에 Flyway V1부터 최신까지 적용된다.
- `ddl-auto: validate`로 Backend가 정상 기동한다.
- Flyway `info/validate`가 성공한다.
- 엔티티 9개가 예상 테이블 9개와 일치한다.

통과 기준: 빈 MySQL에서 Flyway 적용 및 Hibernate validate가 모두 성공한다.

### 단계 2. 로컬 테스트 환경 정리

선행 조건: 단계 1 완료

작업:

1. `application-test.yaml`을 Testcontainers MySQL 8.4 JDBC URL로 전환한다.
2. H2 Driver, H2 Console과 관련 설정을 모두 제거한다.
3. Docker MySQL 8.4와 Redis를 사용하는 기본 로컬 환경을 구성한다.
4. 테스트와 로컬 모두 운영과 동일하게 Flyway 및 `ddl-auto: validate`를 사용한다.
5. AWS 호출이 필요 없는 테스트는 Mock/Fake로 격리하고, S3 통합 테스트는 기본 테스트 실행에서 제외한다.
6. README에 Docker MySQL, Testcontainers와 Workbench 연결 방법을 기록한다.

검증:

- Docker가 실행된 환경에서 AWS 리소스 없이 `./gradlew test`가 통과한다.
- 로컬 Docker MySQL에 Flyway가 적용되고 Backend가 정상 기동한다.
- MySQL Workbench에서 `127.0.0.1:3307`로 로컬 DB를 조회할 수 있다.
- 대표 API 회귀 테스트가 기존 API 계약과 동일한 결과를 낸다.

통과 기준: 로컬 개발과 자동 테스트가 모두 RDS 비용 없이 MySQL 8.4 및 동일한 Flyway schema를 검증한다.

### 단계 3. RDS 및 네트워크 준비

선행 조건: 단계 2 완료

작업 요청자가 수행할 상세 설정은 8장을 따른다.

검증:

- EC2에서 RDS endpoint의 3306 TCP 연결이 가능하다.
- EC2에서 애플리케이션 DB 계정으로 TLS 접속할 수 있다.
- 개발자 PC에서 RDS 3306으로 직접 접속할 수 없어야 한다.
- Workbench는 EC2 SSH 터널을 통해 접속할 수 있어야 한다.

통과 기준: EC2와 승인된 Workbench 경로에서만 DB 접속이 가능하다.

### 단계 4. RDS 빈 스키마 생성

선행 조건: 단계 1과 단계 3 완료

작업:

1. `community` DB를 `utf8mb4`, `utf8mb4_0900_ai_ci` 기준으로 준비한다.
2. RDS master 계정과 Backend용 `community_app` 계정을 분리한다.
3. 현재 Backend가 Flyway를 실행하므로 `community_app`에는 DML과 Flyway DDL 권한을 함께 부여한다.
4. Flyway history와 생성된 테이블·인덱스·제약을 확인한다.

검증:

- Flyway checksum과 적용 버전이 저장소와 일치한다.
- `SHOW CREATE TABLE`, `information_schema` 결과가 확정 스키마와 일치한다.
- Backend를 RDS에 연결해 `ddl-auto: validate` 기동 검증을 하되 아직 운영 트래픽은 전환하지 않는다.

통과 기준: 데이터가 없는 RDS에서 애플리케이션이 스키마 변경 없이 기동한다.

### 단계 5. 데이터 변환 리허설

선행 조건: 단계 4 완료

권장 방식은 Flyway로 RDS 스키마를 먼저 생성한 뒤 H2에서 데이터만 추출해 적재하는 것이다. H2의 전체 DDL을 MySQL에 직접 실행하지 않는다.

작업:

1. H2 복사본에서 테이블 데이터를 CSV 또는 변환 가능한 INSERT 형태로 추출한다.
2. 컬럼 목록을 명시해 적재 파일을 생성한다.
3. Boolean, timestamp 정밀도, enum 문자열, UTF-8/이모지, NULL과 빈 문자열을 변환 검증한다.
4. PK 값을 그대로 보존한다.
5. 외래키 의존 순서에 맞춰 적재한다. 기본 순서는 `users` → `posts` → `comments`, `refresh_token`, `post_likes`, `post_views`, `post_reports`, `post_edit_history`, `drafts`이다.
6. 적재 후 각 테이블의 `AUTO_INCREMENT`를 `MAX(PK) + 1` 이상으로 맞춘다.
7. 민감한 운영 데이터는 개발자 PC에 장기 보관하지 않고 암호화·접근통제·파기 절차를 적용한다.

검증:

- 테이블별 row count와 PK 최소/최대값 비교
- FK orphan 쿼리 결과 0건
- UNIQUE 중복 쿼리 결과 0건
- 필수 컬럼 NULL 위반 0건
- 게시글 `likes/views/comments` 집계값과 관계 테이블·댓글 데이터 비교
- 한글, 이모지, 장문 TEXT, 이미지 URL 샘플 비교
- 로그인 가능한 비밀번호 해시와 Refresh Token 데이터 보존 확인

통과 기준: 정합성 검사 결과가 모두 일치하고 대표 API 회귀 테스트가 통과한다.

### 단계 6. 운영 전환

선행 조건: 단계 0~5 승인

작업:

1. 점검 시간을 공지하고 쓰기 요청을 중단한다. 읽기까지 중단하는 유지보수 모드가 가장 안전하다.
2. EC2 Backend 컨테이너를 중지해 H2에 신규 쓰기가 발생하지 않게 한다.
3. Redis의 Draft 동기화 상태를 확인하고, 필요한 Draft가 H2에 반영된 뒤 최종 백업한다.
4. 최종 H2 데이터 추출·변환·RDS 적재를 실행한다.
5. 단계 5의 정합성 검사를 다시 실행한다.
6. EC2 `.env`에 `SPRING_PROFILES_ACTIVE=prod`와 RDS 접속정보를 설정한다.
7. Compose의 하드코딩된 H2 `DB_URL`, 사용자명, 비밀번호를 환경변수 참조로 변경한다.
8. Backend 컨테이너를 재생성하고 로그, healthcheck, Flyway, Hibernate validate를 확인한다.
9. Smoke test 후 트래픽을 재개한다.

중요: 첫 전환 배포에서는 `backend-data` H2 Volume을 삭제하지 않는다. Compose 연결을 제거하더라도 롤백 보존 기간까지 Volume과 최종 백업을 유지한다.

검증:

- Backend 로그에 RDS 연결, Flyway validate, Hibernate validate 성공이 확인된다.
- H2 파일의 수정 시각이 전환 후 바뀌지 않는다.
- RDS의 신규 회원/게시글 생성 데이터가 Workbench에서 확인된다.
- 전체 핵심 기능 Smoke test가 통과한다.
- S3 업로드와 CloudFront 이미지 조회가 기존 URL 형식으로 동작한다.
- Redis 임시저장 생성, 자동 동기화, 발행이 정상 동작한다.

통과 기준: 기능/API 회귀 없이 모든 신규 관계형 데이터가 RDS에 저장된다.

### 단계 7. GitHub Actions 배포 유지 검증

선행 조건: 단계 6 완료

작업:

1. 기존 `main` push 트리거와 Docker Hub 태그 정책을 유지한다.
2. 기존 이미지 빌드 및 EC2 SSH 배포 단계를 유지한다.
3. Compose 파일 변경으로 `.env`의 RDS 접속정보가 덮어써지지 않는지 확인한다.
4. 배포 healthcheck가 단순 TCP open뿐 아니라 애플리케이션 준비 실패를 탐지할 수 있는지 검토한다.
5. DB 비밀번호가 GitHub Actions 출력, Docker inspect, 저장소에 노출되지 않는지 확인한다.

검증:

- 테스트 커밋 배포가 기존 흐름으로 성공한다.
- 배포 후 Backend가 RDS에 연결한다.
- EC2, Redis, S3, CloudFront 연결이 유지된다.
- 실패 시 commit SHA 이미지 태그로 Backend 이미지를 되돌릴 수 있다.

통과 기준: DB 연결 대상만 변경되고 기존 자동 배포 흐름은 동일하게 동작한다.

### 단계 8. 안정화와 H2 폐기

선행 조건: 운영 관찰 기간 및 승인 완료

작업:

1. RDS CPU, 연결 수, FreeStorageSpace, latency, deadlock, 오류 로그를 모니터링한다.
2. 애플리케이션 DB connection pool과 RDS connection 상한을 점검한다.
3. 자동 백업과 수동 스냅샷 복구 테스트를 수행한다.
4. 합의된 보존 기간 후 H2 Volume을 제거한다.
5. 백업 삭제 시 복구 불가능 여부와 삭제 대상을 재확인한다.

검증:

- 관찰 기간 동안 기능 오류와 데이터 불일치가 없다.
- RDS 스냅샷 또는 Point-in-Time Recovery 복구 절차가 검증된다.
- H2 제거 전 최종 승인과 백업 보존 여부가 기록된다.

통과 기준: RDS가 안정적으로 운영되고 H2 롤백이 더 이상 필요 없다는 승인이 완료된다.

---

## 8. 작업 요청자가 해야 할 AWS·Workbench 설정

### 8.1 RDS 생성 화면 확정값

| 설정 | 선택값 |
|---|---|
| 생성 방식 | 표준 생성 |
| 엔진 | Amazon RDS for MySQL |
| 엔진 버전 | MySQL 8.4 계열 |
| DB 식별자 | `community-mysql` |
| 마스터 사용자 | `community_admin` 권장, `root` 사용 지양 |
| 자격 증명 관리 | 자체 관리 |
| 암호 자동 생성 | 체크하지 않음 |
| DB 인증 | 암호 인증 |
| 배포 | 비용 우선 시 Single-AZ |
| 스토리지 유형 | 범용 SSD `gp3` |
| 할당 스토리지 | 20 GiB |
| 스토리지 자동 확장 | 선택 사항, 사용 시 최대 100 GiB 권장 |
| EC2 연결 | 현재 Spring Boot Backend EC2에 연결 |
| VPC | 반드시 현재 Backend EC2와 동일한 VPC |
| DB Subnet Group | 자동 설정 (`rds-ec2-db-subnet-group-1` 등) |
| Public Access | 아니요 |
| 포트 | 3306 |
| 초기 DB명 | `community` |
| RDS Proxy | 생성하지 않음 |
| 인증 기관 | `rds-ca-rsa2048-g1` 기본값 유지 |
| Database Insights | 표준 |
| Enhanced Monitoring | 초기에는 비활성화 |
| 로그 내보내기 | 초기에는 비활성화 |
| 자동 백업 | 활성화, 보존 7일 권장 |
| 암호화 | 활성화 |
| 마이너 버전 자동 업그레이드 | 활성화 |
| 삭제 방지 | 활성화 |

선택 태그:

```text
Name=community-mysql
Environment=production
Project=ktb-community
```

RDS Proxy와 Database Insights 고급 모드는 현재 단일 EC2/HikariCP 구조에 필요하지 않고 추가 비용 또는 인증 구성을 만들 수 있으므로 사용하지 않는다.

### 8.2 VPC와 EC2 자동 연결

1. EC2 Console에서 현재 Backend 인스턴스의 VPC ID와 Security Group 이름을 확인한다.
2. RDS 생성 화면의 VPC가 EC2 VPC ID와 정확히 같은지 확인한다.
3. `EC2 컴퓨팅 리소스에 연결`을 선택하고 현재 Backend EC2를 지정한다.
4. DB Subnet Group은 자동 설정을 사용한다.
5. `추가 VPC 보안 그룹`이 필수라면 EC2가 실제로 사용하는 Security Group을 선택한다. 예를 들어 실제 EC2 그룹이 `launch-wizard-1`이면 해당 항목을 선택하고 `default`는 선택하지 않는다.
6. 화면 안내에 따라 AWS가 `rds-ec2-1`과 같은 RDS 전용 Security Group을 자동 생성하도록 한다.
7. 생성 후 `rds-ec2-1` 규칙이 정상이면 RDS에 중복 연결된 기존 EC2 Security Group은 제거할 수 있다.

선택 가능한 Security Group 이름을 추측하지 말고 반드시 EC2 상세 화면에서 실제 연결된 그룹과 대조한다. 기존 그룹에 `All traffic → 0.0.0.0/0` 또는 `MySQL 3306 → 0.0.0.0/0` 규칙이 있으면 RDS에 연결하지 않는다.

### 8.3 보안 그룹 검증

RDS 전용 Security Group 인바운드:

| Type | Port | Source | 목적 |
|---|---:|---|---|
| MySQL/Aurora | 3306 | EC2 Backend가 속한 Security Group ID | 애플리케이션 및 SSH 터널 경유 연결 |

RDS 3306에 `0.0.0.0/0` 또는 개발자 공인 IP를 직접 허용하지 않는다. RDS 아웃바운드는 조직 정책에 맞추고 EC2 Security Group 아웃바운드에서 RDS 3306 접근이 가능해야 한다.

EC2 Security Group 인바운드:

- SSH 22는 Workbench를 사용할 승인된 개발자 공인 IP로만 제한한다.
- 기존 HTTP/HTTPS 인바운드 정책은 변경하지 않는다.
- EC2의 Backend 8080과 Redis 6379는 외부에 신규 공개하지 않는다.

RDS 생성 후 확인 경로:

```text
RDS → Databases → community-mysql → 연결 및 보안
→ VPC 보안 그룹 → rds-ec2-1 → 인바운드 규칙
```

### 8.4 DB 계정 및 권한

- RDS master 계정은 초기 설정에만 사용하고 Backend에 사용하지 않는다.
- 현재 구성에서는 Flyway와 애플리케이션이 동일한 `community_app` 계정을 사용한다.
- Workbench 조회에는 `community_readonly` 계정을 권장한다.
- 마스터 암호와 애플리케이션 암호는 서로 다르게 설정하고 안전한 비밀번호 관리자에 보관한다.

마스터 계정으로 실행:

```sql
CREATE USER 'community_app'@'%'
IDENTIFIED BY '<강한 애플리케이션 비밀번호>'
REQUIRE SSL;

GRANT SELECT, INSERT, UPDATE, DELETE,
      CREATE, ALTER, INDEX, DROP, REFERENCES
ON community.*
TO 'community_app'@'%';

CREATE USER 'community_readonly'@'%'
IDENTIFIED BY '<강한 조회 전용 비밀번호>'
REQUIRE SSL;

GRANT SELECT
ON community.*
TO 'community_readonly'@'%';

FLUSH PRIVILEGES;
```

### 8.5 TLS와 파라미터

- 인증 기관은 `rds-ca-rsa2048-g1` 기본값을 유지한다.
- JDBC URL에 `sslMode=REQUIRED`를 지정한다.
- Workbench에는 AWS RDS CA bundle을 설정한다.
- RDS, 애플리케이션 및 데이터 기준 timezone은 UTC로 통일한다.
- DB parameter group에서 character set, collation, timezone, max connections를 확인한다.

### 8.6 MySQL Workbench 연결

권장 연결 방식: `Standard TCP/IP over SSH`

- SSH Hostname: `<EC2 public DNS or Elastic IP>:22`
- SSH Username: 현재 EC2 배포 사용자
- SSH Key File: 해당 EC2 개인키
- MySQL Hostname: RDS Private Endpoint
- MySQL Server Port: `3306`
- Username: `community_readonly`
- SSL: RDS CA를 설정하고 사용

검증 쿼리:

```sql
SELECT VERSION();
SELECT DATABASE();
SHOW TABLES;

SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SHOW INDEX FROM users;
SHOW INDEX FROM posts;
SHOW INDEX FROM comments;
```

Workbench 접속을 위해 RDS Public Access를 활성화하지 않는다.

### 8.7 EC2 배포 환경

- 배포 디렉터리 `.env`에 RDS 환경변수를 추가하고 `chmod 600 .env`를 적용한다.
- EC2 IAM Role의 기존 S3 권한은 변경하지 않는다.
- S3 bucket policy, CORS, CloudFront Origin/OAC 및 공개 URL은 변경하지 않는다.
- Redis container, network, volume 설정은 변경하지 않는다.

```dotenv
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<RDS_ENDPOINT>:3306/community?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&sslMode=REQUIRED
DB_USERNAME=community_app
DB_PASSWORD=<실제 애플리케이션 DB 비밀번호>
DB_MAX_POOL_SIZE=10
DB_MIN_IDLE=2
```

현재 변경된 Backend는 RDS 환경변수가 없으면 정상 기동할 수 없다. 따라서 RDS 생성, 연결 확인, 계정 생성, H2 백업과 데이터 적재 준비가 끝나기 전에 최신 `compose.yml`과 Backend 이미지를 EC2에 배포하지 않는다.

---

## 9. Risk와 대응 방법

| Risk | 영향 | 예방·대응 |
|---|---|---|
| 실제 H2 스키마와 Flyway V1 불일치 | 기동 실패 또는 데이터 누락 | 실제 `INFORMATION_SCHEMA`를 기준으로 3자 비교 후 빈 MySQL에서 `validate` 수행 |
| H2와 MySQL 타입/예약어 차이 | 적재 실패, 값 변형 | DDL은 Flyway로 생성하고 데이터만 이전; Boolean, enum, datetime, TEXT 별도 검증 |
| Hibernate Naming Strategy 차이 | 컬럼을 찾지 못해 기동 실패 | 운영과 동일한 프로필로 로컬 MySQL `ddl-auto: validate` 선행 |
| 마이그레이션 중 신규 쓰기 | H2와 RDS 데이터 불일치 | Backend 중지/유지보수 모드 후 최종 export; 점검 시간 확보 |
| Redis Draft 미동기화 | 최신 임시저장 유실 | 전환 전 Draft 동기화 완료 확인, Redis AOF 백업 유지 |
| FK 순서 또는 orphan 데이터 | INSERT 실패 | 의존 순서 적재, 사전 orphan 검사, 오류 row 격리 및 승인 없는 임의 삭제 금지 |
| PK/AUTO_INCREMENT 충돌 | 전환 후 신규 INSERT 실패 | PK 보존 및 모든 identity 테이블의 다음 AUTO_INCREMENT 검증 |
| 문자셋·이모지 손상 | 사용자 콘텐츠 변형 | `utf8mb4` 강제, 샘플과 길이/checksum 비교 |
| 날짜·timezone 변형 | 게시/수정/만료 시각 오류 | UTC 기준 명시, DATETIME(6) 샘플 비교, JWT/Refresh Token 만료 테스트 |
| ENUM 값 불일치 | 적재 또는 조회 실패 | Java enum, H2 값, MySQL enum 허용값 비교 |
| RDS 직접 인터넷 노출 | 데이터 유출·공격 | Private RDS + SG-to-SG + EC2 SSH Tunnel 사용 |
| DB 비밀번호 노출 | 계정 탈취 | GitHub/Compose 하드코딩 금지, EC2 `.env` 권한 600, 로그 마스킹; Secrets Manager/SSM은 후속 개선 |
| RDS connection 고갈 | API 장애 | Hikari pool 상한 설정, 인스턴스 수 합산, CloudWatch 알람 |
| Flyway와 Hibernate의 동시 DDL 변경 | 예측 불가능한 스키마 변경 | 운영 `ddl-auto: validate`, Flyway만 DDL 소유 |
| 자동 배포가 미완료 migration을 배포 | 기동 실패 | 로컬 MySQL 검증 및 배포 승인 단계; migration backward compatibility 유지 |
| 롤백 후 신규 RDS 데이터 유실 | 사용자 작업 손실 | 초기 안정화 동안 빠른 판단; 롤백 시 쓰기 차단 및 역방향 데이터 처리 여부 결정 |
| RDS 비용 증가 | 예산 초과 | 개발은 Docker MySQL, 테스트는 Testcontainers MySQL 사용; RDS는 운영만 사용하고 20 GiB gp3·표준 Insights·Single-AZ로 시작 |

---

## 10. 롤백 계획

### 10.1 롤백 조건

- Backend가 RDS 연결 또는 Hibernate validate에 지속 실패
- 핵심 API의 요청/응답 또는 기능 회귀 발생
- 데이터 누락, FK 오류, 문자 손상 등 중대한 정합성 문제 발견
- RDS latency/connection 문제로 서비스 안정성 확보 실패

### 10.2 롤백 절차

1. 즉시 쓰기 요청을 중단한다.
2. 장애 시점 이후 RDS에 생성된 신규 데이터 범위를 기록하고 스냅샷을 생성한다.
3. Backend를 중지한다.
4. EC2 환경변수를 최종 H2 백업 기준 설정으로 되돌린다.
5. 필요하면 commit SHA 태그의 이전 Docker 이미지를 사용한다.
6. 보존된 `backend-data` Volume 또는 복원한 H2 파일을 연결한다.
7. Backend 기동 및 기준 Smoke test 후 트래픽을 재개한다.
8. RDS 신규 데이터를 H2로 역이전할지는 자동 수행하지 않고, 데이터 충돌 분석과 책임자 승인을 거쳐 결정한다.

롤백 가능성을 위해 안정화 승인 전에는 H2 Volume, 최종 H2 파일, RDS 스냅샷을 삭제하지 않는다.

---

## 11. 최종 검증 체크리스트

### 데이터

- [ ] 9개 업무 테이블과 `flyway_schema_history`가 존재한다.
- [ ] 테이블별 row count가 H2 기준선과 일치한다.
- [ ] PK 범위와 AUTO_INCREMENT가 정상이다.
- [ ] FK orphan 및 UNIQUE 중복이 없다.
- [ ] 한글, 이모지, TEXT, enum, boolean, datetime 값이 보존됐다.
- [ ] S3/CloudFront 이미지 URL 문자열이 변경되지 않았다.

### 기능/API

- [ ] API 경로와 HTTP method가 변경되지 않았다.
- [ ] 요청/응답 DTO와 상태 코드가 변경되지 않았다.
- [ ] 회원가입, 로그인, 로그아웃, 토큰 갱신이 정상이다.
- [ ] 회원 조회·수정·비밀번호 변경·탈퇴가 정상이다.
- [ ] 게시글 목록·상세·수정·삭제가 정상이다.
- [ ] 댓글·대댓글 생성·조회·수정·삭제가 정상이다.
- [ ] 좋아요, 조회수, 신고, 수정 이력이 정상이다.
- [ ] 임시저장, 자동 동기화, 발행, 정리가 정상이다.
- [ ] Presigned upload, 이미지 검증, S3 저장, CloudFront 조회가 정상이다.

### 인프라·보안

- [ ] RDS는 Public Access가 비활성화되어 있다.
- [ ] RDS 3306은 EC2 Security Group에서만 허용된다.
- [ ] `rds-ec2-1` 등 RDS 전용 Security Group source가 실제 Backend EC2 Security Group이다.
- [ ] RDS Proxy는 비활성화되어 있다.
- [ ] 인증 기관은 `rds-ca-rsa2048-g1` 기본값이다.
- [ ] Database Insights는 표준 모드다.
- [ ] Workbench는 EC2 SSH Tunnel과 조회 전용 계정을 사용한다.
- [ ] JDBC와 Workbench 연결이 TLS를 사용한다.
- [ ] 비밀값이 저장소, 이미지, Actions 로그에 없다.
- [ ] 백업, PITR, 삭제 방지 및 비용 알람이 설정됐다.

### 테스트·배포

- [ ] Testcontainers MySQL 자동 테스트가 AWS 없이 통과한다.
- [ ] 로컬 Docker MySQL 8.4 검증이 AWS 없이 통과한다.
- [ ] 운영 프로필은 Flyway on, `ddl-auto: validate`이다.
- [ ] GitHub Actions 기존 build/push/deploy 흐름이 성공한다.
- [ ] Redis, EC2, S3, CloudFront 구조가 기존과 동일하다.
- [ ] 이전 Docker SHA 이미지와 H2 백업으로 롤백 가능하다.

---

## 12. 권장 실행 순서 요약

```text
기준선·H2 백업
  → 실제 스키마 비교
  → 로컬 MySQL에서 Flyway/기능 검증
  → Private RDS·보안그룹·계정 준비
  → RDS 빈 스키마 검증
  → H2 데이터 이전 리허설
  → 쓰기 중단 및 최종 백업
  → 최종 데이터 적재·정합성 검사
  → EC2 DB 환경변수 전환
  → 기능/API/S3/Redis Smoke test
  → GitHub Actions 재배포 검증
  → 안정화 관찰
  → 승인 후 H2 폐기
```

실제 운영 전환은 로컬 리허설 결과, 최종 백업 복구 확인, RDS 보안 설정, 정합성 검증 SQL, 롤백 담당자와 판단 기준이 모두 준비된 후 진행한다.
