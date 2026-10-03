# 커밋 기준

- 파일 개수가 아니라 하나의 의미 있는 변경 단위를 기준으로 커밋한다.
- 서로 독립적인 변경은 가능하면 분리하되, 커밋 개수를 늘리기 위해 인위적으로 세분화하지 않는다.
- 변경 성격에 맞는 Conventional Commits 타입을 사용한다: `feat`, `fix`, `docs`, `test`, `refactor`, `chore` 등.
- 제목은 간결하고 변경 내용을 바로 이해할 수 있게 구체적으로 작성한다.
- 본문은 변경 이유나 중요한 맥락을 남길 가치가 있을 때만 작성한다.
- 아직 하지 않은 작업이나 검증되지 않은 내용을 완료된 것처럼 표현하지 않는다.
- 각 커밋이 독립적으로 의미 있는 상태인지 확인하고, 필요하면 한 파일의 변경도 나누어 stage한다.

제목 예시:

```text
chore: initialize Spring Boot project
docs: add initial requirements
```
