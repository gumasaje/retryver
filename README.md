# Retryver

Retryver는 외부 시스템으로 HTTP 이벤트를 전달하는 작업을 관리하기 위한 백엔드 프로젝트다.

사전 실험에서는 송신 측에 timeout이 발생해도 수신 측에서 요청이 처리될 수 있음을 확인했다. 같은 이벤트를 다시 보냈을 때 처리 코드가 한 번 더 실행되는 상황도 관찰했다.

이 관찰을 바탕으로, 응답을 확인하지 못한 전달 작업과 재전송에 따른 중복 처리 문제를 다룰 방법을 설계하고 구현·검증하는 것이 목표다. 전달을 실행하는 프로세스가 중단됐을 때 남은 작업을 어떻게 유지하고 복구할지도 다룬다.

현재는 HTTP 접수 API를 Spring JDBC 저장 서비스와 연결했다. Event와 연결된 Delivery를 MySQL에 함께 저장한 뒤 `202 Accepted`와 `PENDING` 상태로 응답한다. 정상 접수와 입력 거부, Delivery 저장 실패 시 전체 롤백을 검증했다.

## 문서

- [초기 요구사항과 사전 실험 근거](docs/requirements/initial-requirements.md)

## 개발 환경

- Java 21
- Spring Boot 4.1.1
- Gradle Wrapper 9.7.1 / Groovy DSL
- Spring MVC / Spring JDBC / Flyway
- 검증 환경: 로컬 MySQL 8.4.11 / InnoDB

## 실행 절차

MySQL을 `localhost:3306`에서 실행한다. 처음 환경을 준비할 때 관리 계정으로 아래 DB와 계정을 만든다. 비밀번호 자리에는 직접 정한 값을 넣는다.

```sql
CREATE DATABASE retryver CHARACTER SET utf8mb4;

CREATE USER 'retryver_app'@'localhost'
    IDENTIFIED BY '앱용_비밀번호';
GRANT SELECT, INSERT ON retryver.*
    TO 'retryver_app'@'localhost';

CREATE USER 'retryver_migrator'@'localhost'
    IDENTIFIED BY '마이그레이션용_비밀번호';
GRANT SELECT, INSERT, UPDATE, DELETE,
      CREATE, ALTER, DROP, INDEX, REFERENCES
ON retryver.* TO 'retryver_migrator'@'localhost';
```

앱은 `retryver_app`으로 저장·조회하고, Flyway는 `retryver_migrator`로 테이블 구조와 적용 이력을 관리한다. 두 계정 모두 권한 범위를 `retryver` DB로 제한한다. 앱 시작과 Spring 통합 테스트 실행 시 Flyway가 `src/main/resources/db/migration`의 SQL을 적용한다.

실행 환경에는 다음 두 변수가 필요하다. 비밀번호는 소스 파일에 적지 않는다.

| 환경 변수 | 값 |
| --- | --- |
| `RETRYVER_DB_PASSWORD` | `retryver_app` 계정 비밀번호 |
| `RETRYVER_MIGRATION_PASSWORD` | `retryver_migrator` 계정 비밀번호 |

아래 명령은 두 환경 변수가 설정된 터미널에서 프로젝트 루트를 기준으로 실행한다. Java 선택 명령은 macOS용이다. 다른 환경에서는 설치된 Java 21로 `JAVA_HOME`을 설정한다. IntelliJ의 프로젝트 JDK와 Gradle JVM도 Java 21로 선택한다.

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew build
```

`build`는 컴파일, 테스트, 패키징을 실행한다. 통합 테스트는 로컬 MySQL을 사용하며 접수 응답과 입력 검증, Event와 Delivery의 저장 및 트랜잭션 롤백을 검증한다.

서버를 시작하고 `Started RetryverApplication` 로그를 확인한다.

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew bootRun
```

실행 중인 서버에 별도 터미널에서 이벤트를 접수한다.

```bash
curl -i -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "example-event-1",
    "eventCategory": "주문 생성",
    "payload": {"orderId": 42},
    "receiverUrl": "http://localhost:9090/events"
  }'
```

정상 접수에서는 `202 Accepted`와 함께 `eventId`, 서버가 생성한 `deliveryId`, `deliveryStatus: "PENDING"`을 담은 JSON을 반환한다. 이는 Retryver가 전달 작업을 접수했다는 의미이며, Receiver의 업무 처리 완료와는 구분한다.

필수 문자열의 null·빈 값·공백만 있는 값, payload 생략과 JSON null은 `400 Bad Request`로 거부한다. 예제를 다시 실행할 때는 새로운 `eventId`를 사용한다. 현재 같은 이벤트 ID의 재접수는 DB 기본 키 제약에 의해 저장에 실패한다.

서버 종료는 실행 터미널에서 `Ctrl+C`로 한다.
