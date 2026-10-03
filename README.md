# Retryver

Retryver는 외부 시스템으로 HTTP 이벤트를 전달하는 작업을 관리하기 위한 백엔드 프로젝트다.

사전 실험에서는 송신 측에 timeout이 발생해도 수신 측에서 요청이 처리될 수 있음을 확인했다. 같은 이벤트를 다시 보냈을 때 처리 코드가 한 번 더 실행되는 상황도 관찰했다.

이 관찰을 바탕으로, 응답을 확인하지 못한 전달 작업과 재전송에 따른 중복 처리 문제를 다룰 방법을 설계하고 구현·검증하는 것이 목표다. 전달을 실행하는 프로세스가 중단됐을 때 남은 작업을 어떻게 유지하고 복구할지도 다룬다.

기본 Spring Boot 프로젝트를 바탕으로 개발을 시작한다.

## 개발 환경

- Java 21
- Spring Boot 4.1.1
- Gradle Wrapper 9.7.1 / Groovy DSL
- Spring MVC 및 기본 테스트 의존성

## 실행 절차

프로젝트 루트에서 실행한다. 아래 Java 선택 명령은 macOS용이다. 다른 환경에서는 설치된 Java 21로 `JAVA_HOME`을 설정한다. IntelliJ의 프로젝트 JDK와 Gradle JVM도 Java 21로 선택한다.

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew build
```

`build`는 컴파일, 테스트, 패키징을 실행한다. `BUILD SUCCESSFUL`과 테스트 통과를 확인한다. 기본 `contextLoads` 테스트는 Spring 구성 로딩을 확인한다.

서버를 시작하고 `Started RetryverApplication` 로그를 확인한다.

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew bootRun
```

실행 중인 서버를 별도 터미널에서 확인한다.

```bash
curl -i http://localhost:8080/
```

현재 `/` API가 없으므로 404 응답이 예상된다. 이 확인은 서버 기동과 HTTP 응답 확인이며 이벤트 전달 기능 테스트는 아니다. 서버 종료는 실행 터미널에서 `Ctrl+C`로 한다.
