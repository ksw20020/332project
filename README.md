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
 - 워커 모듈 설계 [link](doc/module%20diagram/Worker/Worker_Module_Diagram.png)
 - 마스터 모듈 설계 [link](doc/module%20diagram/Master/master.png)
 - grpc Stream 방식 확정 [link](doc/module%20diagram/Worker/Control_stream.png)
 - 
### Week 5 [link](https://github.com/ksw20020/332project/pull/5)
코드 작성 시작
 - 마스터 - 워커 간 통신 하위 모듈 구현
   - 샘플링, 셔플링 시 조율 부분
 - 단일 파일 정렬 구현
   - Partitioning을 고려한 파일 입출력 구현 중
 - 마스터 내부 샘플링 구현
 - 셔플링 내부 fault tolerance용 인터페이스 구현
  
#### goal of next week
| Member | Goal |
| ---- | ---- |
| 김성원 | Partitioning, k-way merge sort 구현 |
| 진시완 | Registration 구현 |
| 채동욱 | shuffling 중 worker-worker 통신 부분 구현 |

### Week 6 [link](https://github.com/ksw20020/332project/pull/6)
1차 구현 완료
 - 마스터 - 워커 Registration 구현
 - 워커 - 워커 간 셔플링 구현
   - 더미 데이터로 테스트
 - 샘플링 코드 bidirectional streaming으로 전환
 - Partitioning 구현
   - k way merge sort 구현
 - 디스크 IO repository 구현
   - 개발자 로컬 테스트

#### goal of next week
| Member | Goal |
| ---- | ---- |
| 김성원 | 스크립트 작성 및 워커 세팅, 디버깅 |
| 진시완 | fault tolerance 관련 버그 픽스, 스크립트 작성 및 워커 세팅, 디버깅 |
| 채동욱 | future 연결,  디버깅 |

### Week 7
1차 통합 및 테스트 완료
 - 마스터, 워커 SCP 스크립트 작성
 - 마스터, 워커에서 분산 정렬 1차 테스트 성공
 - 오류 케이스 수정
   - 워커가 스스로 담당할 파트가 output에 나오지 않는 문제
   - 폴더 구조가 사전에 생성되지 않으면 진행이 안되는 문제
 - fault tolerance 관련해 빠진 부분 확인
   - 워커와 마스터 간 마지막 종료 시그널 추가 구현 예정
   - 이후 테스트 예정

#### goal of next week
| Member | Goal |
| ---- | ---- |
| 김성원 | 스크립트 작성 및 디버깅 |
| 진시완 | 종료 시그널 구현, 디버깅 |
| 채동욱 | 7주차에 발생한 오류 케이스 수정,  디버깅 |

<br>
<br>
<br>
<br>
<br>

| Week | Goal |
| ---- | ---- |
| Week6 | worker, master 프로그램 작성 | 
| Week7 | 코드 통합, 테스트 및 디버깅 | 
| Week8 | falut tolerance 고려해 테스트 및 디버깅 | 
