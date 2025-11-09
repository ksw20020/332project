# 332project

## Weekly Progress

### Week 1 [link](https://github.com/ksw20020/332project/pull/1)
리포지토리 개설<br>
샘플링 기법 탐색 [link](doc/sampling.md)<br>
통신 시퀀스 다이어그램 작성 [link](doc/PC%20process/PC%20process.puml)<br>

### Week 2 [link](https://github.com/ksw20020/332project/pull/2)
클러스터 권한 획득 및 접속, scp 테스트<br>
K-way Merge Sort 조사 [link](doc/K-way%20Merge.md)<br>
Shuffling 통신 시퀀스 다이어그램 작성 [link](doc/PC%20process/Shuffling.puml)<br>

### Week 3 [link](https://github.com/ksw20020/332project/pull/3)
역할 배분
<details>

- master - Worker 사이의 통신 - 진시완
   1. 샘플링
   2. registration
   3. 완료 확인 통신
- Worker 내부 Sorting & Partitioning(난이도 하 / 코드 양 매우매우 많음) - 김성원
   1. sorting
   2. partitioning
- Shuffling - 채동욱
   1. worker - worker 사이의 데이터교환
   2. master - worker 사이에서 다음 라운드 시작을 통지하는 broadcasting
   
</details>

register 통신 프로토콜 확정(handshake), 다이어그램 업데이트<br>
shuffling 마스터-워커 통신 프로토콜 확정(스트리밍), 다이어그램 업데이트<br>
shuffling 라운드 로빈 페어 매칭 알고리즘 확정<br>

### Week 4 [link](https://github.com/ksw20020/332project/pull/4)
jdk 등 개발환경 세팅, 프로젝트 추가 <br>
클래스 다이어그램 작성
 - 워커 모듈 설계[link](doc/module%20diagram/)
 - 마스터 모듈 설계
 - grpc Stream 방식 확정


#### goal of next week
| Member | Goal |
| ---- | ---- |
| 김성원 | 로컬 sorting 코드 구현 |
| 진시완 | 샘플링 구현 |
| 채동욱 | shuffling 중 worker-master 통신 부분 구현 |

<br>
<br>
<br>
<br>
<br>

| Week | Goal |
| ---- | ---- |
| Week5 | worker, master 프로그램 작성 | 
| Week6 | worker, master 프로그램 작성 | 
| Week7 | falut tolerance 고려해 테스트 및 디버깅 | 
| Week8 | 디버깅 | 
