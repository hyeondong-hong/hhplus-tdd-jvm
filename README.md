# [Chapter 1-1] Test Driven Development

## 🗓️ Weekly Schedule Summary: 이번 챕터의 주간 일정 


### `Default`

- `/point` 패키지 (디렉토리) 내에 `PointService` 기본 기능 작성
- `/database` 패키지의 구현체는 수정하지 않고, 이를 활용해 기능을 구현
- 각 기능에 대한 단위 테스트 작성

> 총 4가지 기본 기능 (포인트 조회, 포인트 충전/사용 내역 조회, 충전, 사용) 을 구현합니다.


### `Step 1`

- 포인트 충전, 사용에 대한 정책 추가 (잔고 부족, 최대 잔고 등)
- 동시에 여러 요청이 들어오더라도 순서대로 (혹은 한번에 하나의 요청씩만) 제어될 수 있도록 리팩토링
- 동시성 제어에 대한 통합 테스트 작성


### `Step 2`

- 동시성 제어 방식에 대한 분석 및 보고서 작성 ( **README.md** )

**위 요구사항을 만족시키고, Github Repository URL 을 제출해주세요**

**만약 `Default` 과제를 만족하지 못하였다면, `Step 1, 2` 는 fail 처리됩니다.**


### ℹ️ 과제 필수 사항

- Nest.js 의 경우 Typescript , Spring 의 경우 Kotlin / Java 중 하나로 작성합니다.
    - 프로젝트에 첨부된 설정 파일은 수정하지 않도록 합니다.
- 테스트 케이스의 작성 및 작성 이유를 주석으로 작성하도록 합니다.
- 프로젝트 내의 주석을 참고하여 필요한 기능을 작성해주세요.
- 분산 환경은 고려하지 않습니다.


### ❓ [과제] `point` 패키지의 TODO 와 테스트코드를 작성해주세요.

**요구 사항**

- PATCH  `/point/{id}/charge` : 포인트를 충전한다.
- PATCH `/point/{id}/use` : 포인트를 사용한다.
- GET `/point/{id}` : 포인트를 조회한다.
- GET `/point/{id}/histories` : 포인트 내역을 조회한다.
- 잔고가 부족할 경우, 포인트 사용은 실패하여야 합니다.
- 동시에 여러 건의 포인트 충전, 이용 요청이 들어올 경우 순차적으로 처리되어야 합니다.

---

# 동시성 제어 방식에 대한 분석 및 보고서

## 🧑‍💻 진행 과정

동시성 처리를 위해 가장 먼저 떠오른 건 **DB 트랜잭션**이지만, 이번 첫 과제에서는 논리적으로 DB를 구현한 객체를 사용해서
API를 통해 가상의 테이블에 데이터를 삽입하거나 수정하는 식으로 **DB를 시뮬레이션** 하도록 출제됐다.

### `synchronized`

따라서 가장 먼저 떠오른 건 동기처리를 위해 자바에서 제공하는 `synchronized`였다. 메소드 자체나 원하는 블록 위에서
`synchronized`를 선언 후 로직을 작성하면 트랜잭션과 유사한 처리가 가능하다.

```java
// PointServiceImpl

private final UserPointTable userPointTable;
private final PointHistoryTable pointHistoryTable;

@Override
public synchronized UserPoint chargePoint(Long userId, long amount) {
    // ...
    return userPointTable.insertOrUpdate(userId, newAmount);
}

@Override
public synchronized UserPoint usePoint(Long userId, long amount) {
    // ...
    return userPointTable.insertOrUpdate(userId, newAmount);
}

@Override
public synchronized UserPoint getUserPoint(Long userId) {
    // ...
    return userPointTable.selectById(userId);
}

@Override
public synchronized PointHistory insertHistory(Long userId, long amount, TransactionType transactionType, long millis) {
    // ...
    return pointHistoryTable.insert(userId, amount, transactionType, millis);
}

@Override
public synchronized List<PointHistory> getPointHistoryAll(Long userId) {
    // ...
    return pointHistoryTable.selectAllByUserId(userId);
}
```

하지만 이렇게 작성하면 **각 메소드에 대해서만 동기화가 이뤄질 뿐**, 가령 `insertHistory`와 `getPointHistoryAll` 메소드가 
동시에 호출되거나 하는 상황으로 인해 레이스컨디션 문제가 발생할 수 있다. 따라서 아래와 같이 처리 가능하다.

```java
// PointServiceImpl

// ... 생략 ...

@Override
public UserPoint getUserPoint(Long userId) {
    synchronized (this) {
        // ...
        return userPointTable.selectById(userId);
    }
}

// ... 생략 ...

@Override
public List<PointHistory> getPointHistoryAll(Long userId) {
    synchronized (this) {
        // ...
        return pointHistoryTable.selectAllByUserId(userId);
    }
}
```

이러면 **`PointServiceImpl` 객체 인스턴스에 대해 동기화가 이뤄지기 때문**에 위 메소드 중 하나라도 실행된다면 다른 메소드로
접근할 경우에도 대기 상태에 들어간다. 하지만 다른 유저가 포인트를 사용하고 충전하는데 왜 나 까지 기다려야 하는가?
뭔가 불합리하고 심기가 불편하니 유저 별로 동기화를 분리하기로 한다.

```java
// PointServiceImpl

private Map<Long, Object> locks = new HashMap<>();

// ... 생략 ...

@Override
public UserPoint getUserPoint(Long userId) {
  synchronized (locks.computeIfAbsent(userId, uid -> new Object())) {
    // ...
    return userPointTable.selectById(userId);
  }
}

// ... 생략 ...

@Override
public List<PointHistory> getPointHistoryAll(Long userId) {
  synchronized (locks.computeIfAbsent(userId, uid -> new Object())) {
    // ...
    return pointHistoryTable.selectAllByUserId(userId);
  }
}
```

이렇게 처리함으로써 유저별로 동기화는 가능해진다. 하지만 **서로 다른 테이블 끼리도 서로 락이 걸리는 부분**이 맘에 걸린다.
`MySQL`에서 사용됐다가 지금은 도태돼서 언급도 안 되는 `MyISAM` 엔진도 락을 걸 때 테이블은 구분한다. 자존심이 상하니
이 부분에 대한 처리도 해준다.

```java
public enum TableType {
  USER_POINT, POINT_HISTORY
}
```

```java
// PointServiceImpl

private Map<String, Object> locks = new HashMap<>();

// ... 생략 ...

@Override
public UserPoint getUserPoint(Long userId) {
  synchronized (locks.computeIfAbsent(userId + "_" + TableType.USER_POINT, uid -> new Object())) {
    // ...
    return userPointTable.selectById(userId);
  }
}

// ... 생략 ...

@Override
public List<PointHistory> getPointHistoryAll(Long userId) {
  synchronized (locks.computeIfAbsent(userId + "_" + TableType.POINT_HISTORY, uid -> new Object())) {
    // ...
    return pointHistoryTable.selectAllByUserId(userId);
  }
}
```

적당히 만족스러워졌다고 생각했지만 `synchronized`에 대한 새로운 정보를 듣게 됐다.

> `synchronized`는 해당 블록에 대한 동시 접근을 제한하여 한 번에 하나의 스레드만 해당 블록을 실행하도록 보장하지만,
 둘째, 셋째 요청이 어느 순서로 처리될지는 보장하지 않는다. 그 이유는 **JVM 스케줄링**에 따라 스레드가 실행되기
 때문인데, 어떤 스레드가 먼저 `synchronized` 블록에 진입할지는 **운영체제**나 **JVM**의 스케줄러에 의존하기 때문에
 **대기 중인 스레드 간의 순서**는 보장되지 않는 것이 특징이다.

요구사항에는 분명히 **동기화 처리**가 아니라 **순차 처리**를 요구했기 때문에 `synchronized`는 해결책이 아니라 판단했다.


### `ReentrantLock`

그래서 **`synchronized`와 유사하면서 대기 순서를 보장하는 락**이 필요했다. 다행히 `Java`는 다양한 락을 지원했는데,
`synchronized` 처럼 재진입을 허용함으로써 교착상태 위험을 피하면서도 대기 순서를 보장하는 락이 필요했고,
그중에서도 `ReentrantLock`이 눈에 띄었다.

`ReentrantLock`은 이름 그대로 **재진입을 허용하는 락**이다. 마치 이번 과제를 위해 준비된 것 처럼 대기 순서 보장 여부
까지 설정 가능하다! 바로 사용해 보기로 했지만, **`lock`과 `acquire`를 직접 제어**해야 해서 기존 `synchronized` 처럼 편한
길은 준비되지 않았다. 그럼 불편하게 써야할까? 컴포넌트를 만들어 보기로 했다.

```java
@Component
public final class VirtualTransaction {

    private final Map<String, Lock> locks = new WeakHashMap<>();

    private Lock getLock(String scope) {
        return locks.computeIfAbsent(scope, k -> new ReentrantLock(true));
    }

    public <T> T perform(String scope, Supplier<T> func) {
        Lock lock = getLock(scope);
        lock.lock();
        try {
            return func.get();
        } finally {
            lock.unlock();
        }
    }
}
```

먼저 기존 `PointServiceImpl`에 구현되어 있던 `locks`를 `HashMap`에서 `WeakHashMap`으로 변경했다. 왜냐면 락이 끝나면
소명을 다 했을 락 인스턴스들이 다음 요청이 언제 올 지도 모르는 상황에서 메모리 자원 위에 눌러 앉아 방탕한 삶을 시작할
것이 뻔하기 때문이다. 이 `WeakHashMap`은 값을 **할당한 키의 메모리 해제 상태를 GC로 부터 전달 받아 값 까지 자동으로
해제**해 주는 이 상황에 가장 필요로 하는 기능을 지닌 맵 클래스다. 따라서 해당 클래스의 기능에 맞게 `perform` 메소드를
구성했다.

먼저 `perform` 메소드는 매개변수로 서로 다른 락임을 식별하는 `scope`를 받아 `WeakHashMap`으로부터 락을 꺼낸다.
그 이후 해당 락에서 `lock` 메소드를 실행해서 락을 건다. 만약 여러 스레드에서 동시에 같은 `scope` 매개변수를 통해 
들어왔다고 해도 락을 걸기 전 까지는 실행이 될 것이다. 이로 인해 작업이 완료되고 첫번째 스레드가 블록을 빠져나온다
하더라도 다른 스레드에서 동일한 `scope`를 쥐고 있으니 GC는 메모리를 해제하지 않고, 따라서 `WeakHashMap`은 값 까지
메모리를 해제하지는 않을 거라는 추론이다. 여기서 키를 `String`으로 쓰고 있다는 점이 걸렸는데, 다른 스레드에서 생성된
`String` 객체는 주소가 다를 수 있기 때문이다. 만약 이전에 사용된 키의 주소로 할당된 값이 블록을 빠져나와 GC를 통해
메모리가 해제된다면 그 신호를 받고 값 까지 메모리가 해제될까에 대한 우려였다. 이 부분에 대해 알아보니 아래와 같았다.

> `WeakHashMap`에서 키로 사용되는 객체는 동일한 객체인지 여부를 `equals`와 `hashCode` 메소드를 통해 결정한다.
 즉, 객체의 메모리 주소가 다르더라도, 두 객체가 동일한 `equals` 결과를 반환하고, 같은 `hashCode` 결과값을 가지고
 있다면 동일한 키로 인식된다.

다행이었다. 이런 사실과 달랐다면 키 까지 관리하는 로직을 작성할 뻔 했다. 이론대로라면 `WeakHashMap`은 정상적으로
동작해줄 것이다.

그럼 다시 `perform` 메소드로 돌아가서, 서로 어느 것에 대해 락을 걸 지에 대한 `scope` 매개변수를 받아서 락을 꺼내오고,
락에 대한 `lock` 메소드를 실행한 뒤, 두번째 매개변수로 받은 함수를 실행 후 `finally` 블록에서 락에 대한 `unlock`
메소드를 실행해서 블록을 종료한다. 그럼 해당 컴포넌트를 사용한 `PointServiceImpl`은 아래 처럼 된다.

```java
// PointServiceImpl

private final VirtualTransaction virtualTransaction;

private String makeScope(Long userId, TableType tableType) {
    return userId + "_" + tableType;
}

// ... 생략 ...

@Override
public UserPoint getUserPoint(Long userId) {
    return virtualTransaction.perform(makeScope(userId, TableType.USER_POINT), () -> {
        // ...
        return userPointTable.selectById(userId);
    });
}

// ... 생략 ...

@Override
public List<PointHistory> getPointHistoryAll(Long userId) {
    return virtualTransaction.perform(makeScope(userId, TableType.POINT_HISTORY), () -> {
        // ...
        return pointHistoryTable.selectAllByUserId(userId);
    });
}
```

이러면 유저와 테이블에 관한 각각의 락을 안전하게 설정할 수 있을 것이다. 하지만 테이블의 구조를 다시 살펴보니 코드상에
문제점이 있어서 아래와 같이 수정했다.

```java
// PointServiceImpl

// ... 생략 ...

@Override
public UserPoint getUserPoint(Long userId) {
    return virtualTransaction.perform(makeScope(userId, TableType.USER_POINT), () -> {
        // ...
        return userPointTable.selectById(userId);
    });
}

// ... 생략 ...

@Override
public List<PointHistory> getPointHistoryAll(Long userId) {
    return virtualTransaction.perform(TableType.POINT_HISTORY.toString(), () -> {
        // ...
        return pointHistoryTable.selectAllByUserId(userId);
    });
}
```

`PointHistoryTable` 인스턴스 내의 `table`을 보면 `ArrayList` 이기 때문에 유저와 관계없이 하나의 콜렉션에 접근하는
형태가 될 것이고, 이는 **동시 접근 시 레이스컨디션 문제**로 이어질 것이다. 따라서 `PointHistoryTable`에 접근할 때에는
유저 구분을 없애고 모든 유저가 순차적으로 히스토리를 쌓는 형태가 된다.

여기서 scope 문자열을 생성하는 코드가 지저분하다는 생각이 들어 아래와 같이 변경했다.

```java
public enum TableType {
    USER_POINT, POINT_HISTORY;

    public String makeScope(Long id) {
        StringBuilder scope = new StringBuilder();
        if (id != null) {
            scope.append(id).append("_");
        }
        return scope.append(this.name()).toString();
    }

    public String makeScope() {
        return makeScope(null);
    }
}
```

```java
// PointServiceImpl

// ... 생략 ...

@Override
public UserPoint getUserPoint(Long userId) {
    return virtualTransaction.perform(TableType.USER_POINT.makeScope(userId), () -> {
        // ...
        return userPointTable.selectById(userId);
    });
}

// ... 생략 ...

@Override
public List<PointHistory> getPointHistoryAll(Long userId) {
    return virtualTransaction.perform(TableType.POINT_HISTORY.makeScope(), () -> {
        // ...
        return pointHistoryTable.selectAllByUserId(userId);
    });
}
```

이렇게 함으로써 scope 생성 책임을 `TableType` enum 에게 전가할 수 있고, `VirtualTransaction` 컴포넌트를 테스트할 때
스코프 생성에 대한 중복 로직을 작성할 필요도 없어진다.

추가로 테스트를 하다가 발견한 문제인데, `VirtualTransaction`에서 락을 취득하는 부분에서도 레이스 컨디션 문제가 발생했다.

```java
@Component
public final class VirtualTransaction {

    private final Map<String, Lock> locks = new WeakHashMap<>();

    private Lock getLock(String scope) {
        return locks.computeIfAbsent(scope, k -> new ReentrantLock(true));  // 이 부분
    }

    public <T> T perform(String scope, Supplier<T> func) {
        Lock lock = getLock(scope);
        lock.lock();
        try {
            return func.get();
        } finally {
            lock.unlock();
        }
    }
}
```

`locks` 인스턴스에 여러 스레드가 동시에 접근해서 동일한 키로 두 개의 락이 발생하고 있어서 아래와 같이 처리해 봤다.

```java
@Component
public final class VirtualTransaction {

    private final Map<String, Lock> locks = new WeakHashMap<>();
    private final ReentrantLock globalLock = new ReentrantLock(true);
  
    private Lock getLock(String scope) {
        globalLock.lock();
        try {
            return locks.computeIfAbsent(scope, k -> new ReentrantLock(true));
        } finally {
            globalLock.unlock();
        }
    }
  
    // ... 생략 ...
}
```

### `ConcurrentHashMap`

하지만 이번주 멘토링을 통해 얻은 키워드 중 `ConcurrentHashMap`이라는 클래스가 있었다.
이 클래스에 대해 알아보니 동기처리를 보장하는데, 무려 그 범위가 키 단위라고 한다. 그렇다면 `ConcurrentHashMap`으로
처리하는 편이 성능적으로 훨씬 이점이 많다.

```java
@Component
public final class VirtualTransaction {

    private final Map<String, Lock> locks = new ConcurrentHashMap<>();
  
    public Lock getLock(String scope) {
        return locks.computeIfAbsent(scope, k -> new ReentrantLock(true));
    }
  
    // ... 생략 ...
}
```

이러면 `WeakHashMap`의 이점은 사라지지만, 키를 정리하는 로직 까지 직접 구현하기엔 시간이 모자라니 아쉽더라도 이 정도로
정리하는 편이 좋아 보인다. 또한 테스트 코드에서 접근할 수 있도록 `getLock` 메소드를 공개 상태로 변경했다.


## 📊 테스트 코드 작성

이제 실제로 테스트를 해볼 차례다. TDD는 커녕 테스트 코드는 운영에 반영하기 전 직접 시뮬레이션 해보는 수준 밖에 작성해본
적이 없지만, 오프라인에서 **테스트 코드를 잘 작성하면 기능 하나를 바꿨을 때 어느 포인트가 영향을 받는지 클릭 한 번으로
알 수 있다**고 소개하신 내용이 정말 인상이 깊어서 꼭 배우고 넘어가야겠다고 생각했다. 먼저 서비스 운영을 해본 입장에선
아무리 하드코딩을 최소화 하고 모듈화를 열심히 하더라도 작업자가 사람인 이상 실수는 반드시 할 수 있고, 팀 단위로 일을
할 때 함께 작업하는 사람이 많으면 많을 수록 테스트 코드는 필수가 되어야만 한다는 생각이 들었다.

### `UnitTest`

먼저 단위 테스트를 작성하기 위해 아래와 같이 `Mock`을 포함한 테스트 클래스의 틀을 만들었다.

```java
@ExtendWith(MockitoExtension.class)
public class PointServiceUnitTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @Mock
    private VirtualTransaction virtualTransaction;

    @Mock
    private PointValidator pointValidator;

    @InjectMocks
    private PointServiceImpl pointService;

    @BeforeEach
    public void init() {

    }
}
```

여기서 `@BeforeEach` 어노테이션이 붙은 메소드에 단위 테스트 전체에 사용할 `Mock`을 작업해 준다.

```java
    // ... 생략 ...

    private Long targetUserId;

    @BeforeEach
    public void init() {
        targetUserId = new Random().nextLong();

        // 사용하고자 하는 scope에만 Lock 인스턴스 Stubbing
        Lock userPointLock = new ReentrantLock(true);
        lenient().when(virtualTransaction.getLock(eq(TableType.USER_POINT.makeScope(targetUserId)))).thenReturn(userPointLock);
        Lock pointHistoryLock = new ReentrantLock(true);
        lenient().when(virtualTransaction.getLock(eq(TableType.POINT_HISTORY.makeScope()))).thenReturn(pointHistoryLock);

        // 가상 트랜잭션 처리 후 Supplier의 실제 로직처리 및 반환값을 가져오기 위해 CallRealMethod 반환으로 설정
        lenient().when(virtualTransaction.perform(any(String.class), any())).thenCallRealMethod();

        // insertOrUpdate의 반환값은 항상 입력된 매개변수로 만들어진 UserPoint 레코드 인스턴스
        lenient().when(userPointTable.insertOrUpdate(eq(targetUserId), any(Long.class))).thenAnswer(
                invocation -> new UserPoint(invocation.getArgument(0), invocation.getArgument(1), System.currentTimeMillis()));
    }

    // ... 생략 ...
```

먼저 `PointService`의 가상 테이블에 접근하는 로직은 모두 `VirtualTransaction` 컴포넌트를 참조하고 있으니 사용하고자
하는 scope에 대한 락 반환과 `perform`메소드 실행 시 `PointService`에 정의된 로직이 그대로 실행돼야 하니 `CallRealMethod`
반환으로 처리했다. 단위 테스트에서는 `PointValidator`를 포함한 `PointService`의 기본 기능이 제대로 동작하는지만 테스트
할 것이기 때문에 동시성에 관련된 `VirtualTransaction`에 관한 로직은 간략하게 작성하고 넘어가기로 했다.

다음으로 실제 단위 테스트를 진행하는 테스트 블록을 작성했다.

```java

    // ... 생략 ...

    @Test
    @DisplayName("포인트 충전 실패")
    public void failure_chargePoint() {
        // given
        UserPoint userPoint = new UserPoint(targetUserId, CHARGE_LIMIT - 5000L, System.currentTimeMillis());
        when(userPointTable.selectById(targetUserId)).thenReturn(userPoint);

        // when
        lenient().doThrow(new IllegalArgumentException("입력 포인트 값이 0 이하"))
                .when(pointValidator).validateCharge(eq(userPoint), leq(0L));
        lenient().doThrow(new IllegalArgumentException("최대 충전 포인트 초과"))
                .when(pointValidator).validateCharge(eq(userPoint), gt(CHARGE_LIMIT - userPoint.point()));

        // then
        Exception enterNegativeValue = assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(targetUserId, -100L));
        assertEquals("입력 포인트 값이 0 이하", enterNegativeValue.getMessage());
        Exception maximumInputExceed = assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(targetUserId, 5000L + 1L));
        assertEquals("최대 충전 포인트 초과", maximumInputExceed.getMessage());
    }

    @Test
    @DisplayName("포인트 사용 실패")
    public void failure_usePoint() {
        // given
        UserPoint userPoint = new UserPoint(targetUserId, 3000L, System.currentTimeMillis());
        when(userPointTable.selectById(targetUserId)).thenReturn(userPoint);

        // when
        lenient().doThrow(new IllegalArgumentException("입력 포인트 값이 0 이하"))
                .when(pointValidator).validateUse(eq(userPoint), eq(-100L));
        lenient().doThrow(new IllegalArgumentException("포인트 부족"))
                .when(pointValidator).validateUse(eq(userPoint), gt(userPoint.point()));

        // then
        Exception enterNegativeValue = assertThrows(IllegalArgumentException.class, () -> pointService.usePoint(targetUserId, -100L));
        assertEquals("입력 포인트 값이 0 이하", enterNegativeValue.getMessage());
        Exception exceedRemaining = assertThrows(IllegalArgumentException.class, () -> pointService.usePoint(targetUserId, userPoint.point() + 1L));
        assertEquals("포인트 부족", exceedRemaining.getMessage());
    }

    // ... 생략 ...

```

먼저 테스트를 하기 위해 `targetUserId`로 `@BeforeEach` 어노테이션 블록에서 정의된 유저ID에 해당하는 유저의 포인트 잔량과
해당 정보들을 포함한 레코드 객체 인스턴스를 할당하고, `UserPointTable.selectById(targetUserId)`를 호출하면 반환할 수
있도록 해당 인스턴스를 `.thenReturn()` 메소드에 매개변수로 설정해서 Stubbing한다. 이러면 테스트에서 사용하고자 하는
유저ID에 대해 포인트를 충전하고 사용하고자 하는 기준이 세워진다.

`포인트 충전 실패` 단위 테스트 블록에서는 포인트를 충전할 때 유저 입력으로 인해 발생할 수 있는 오류를 정의하고,
`포인트 사용 실패` 단위 테스트 블록에서는 포인트를 사용할 때 유저 입력으로 인해 발생할 수 있는 오류를 정의하기로 했다.
먼저 충전 관련 테스트에선 음수 포인트를 충전할 순 없으니 음수 입력에 대한 assert, `CHARGE_LIMIT`라는 Constants로 정의된
최대 포인트 보유량에 대한 assert를 작성했고, 사용 관련 테스트에선 마찬가지로 음수 포인트를 사용할 순 없으니 음수 입력에
대한 assert, 현재 보유 중인 포인트 보다 큰 사용량에 대한 assert를 작성했다.

마지막으로 Stubbing한 로직들을 수행해서 결과를 확인한다. 만약 검증이 제대로 되지 않았다면 `AssertionError`가 발생하고
테스트는 실패할 거다. 오류 발생 시 각각 `IllegalArgumentException`을 부여했으니 기댓값으로 `IllegalArgumentException`를
주고, 추가적으로 발생한 예외의 메시지도 함께 비교해서 교차검증 해주기로 했다.

그럼 테스트 코드 작성이 완료된 것 같으니 테스트코드를 실행해 준다.

> &gt; Task :compileJava<br>
 &gt; Task :processResources UP-TO-DATE<br>
 &gt; Task :classes<br>
 &gt; Task :compileTestJava<br>
 &gt; Task :processTestResources NO-SOURCE<br>
 &gt; Task :testClasses<br>
 OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended<br>
 &gt; Task :test<br>
 Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.<br>
 You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.<br>
 For more on this, please refer to https://docs.gradle.org/8.4/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.<br>
 BUILD SUCCESSFUL in 2s<br>
 4 actionable tasks: 3 executed, 1 up-to-date<br>
 오후 8:17:52: 실행이 완료되었습니다 ':test --tests "io.hhplus.tdd.point.unit.service.PointServiceUnitTest"'.

메시지에선 과제와 크게 관련은 없는 추가 정보를 몇 가지 포함하고 있지만 우선 **테스트는 정상적으로 통과**됐다.


### `IntegrationTest`

이번엔 통합 테스트를 작성해야 한다. 이번엔 `@Autowired` 어노테이션으로 실제 의존성 주입을 통해 테스트를 진행하기로 한다.

```java

@SpringBootTest
public class PointServiceIntegrationTest {

    @Autowired
    private PointService pointService;

    private Long targetUserId;

    @BeforeEach
    public void init() {
        targetUserId = new Random().nextLong();
    }
}
```

마찬가지로 통합 테스트에서도 `@BeforeEach` 어노테이션이 붙은 메소드에 단위 테스트 전체에 사용할 데이터를 작업해 준다.
실제 의존성주입을 통해 진행하는 테스트이기 때문에 컴포넌트 메소드에 대해 Stubbing을 굳이 할 필요는 없고, 각 테스트에서
사용할 유저ID만 미리 셋업하기로 했다.

```java
    // ... 생략 ...

    @Test
    @DisplayName("동시 접근 안정성 테스트")
    public void success_validateConcurrentConnection() throws InterruptedException {
        UserPoint chargedPoint = pointService.chargePoint(targetUserId, 10000L);
        assertEquals(chargedPoint.point(), 10000L);

        long[] plan = {700L, -300L, 950L, 1200L, -650L, -200L, 600L, -1100L};

        CountDownLatch latch = new CountDownLatch(plan.length);
        CountDownLatch standbyLatch = new CountDownLatch(1);

        for (long amount : plan) {
            Runnable runnable = (amount > 0)
                    ? () -> pointService.chargePoint(targetUserId, amount)
                    : () -> pointService.usePoint(targetUserId, -amount);
            CompletableFuture.runAsync(() -> {
                try {
                    standbyLatch.await();
                    runnable.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        standbyLatch.countDown();
        latch.await();

        assertEquals(pointService.getUserPoint(targetUserId).point(), Arrays.stream(plan).sum() + 10000L);
        pointService.getPointHistoryAll(targetUserId).forEach(System.out::println);

    }

    // ... 생략 ...
```

작성할 코드는 특정 유저에 대해 충전과 사용을 거의 동시에 요청해서 최종적으로 실제 요구한 처리와 동일한 포인트 잔량을
가지고 있는지에 대한 테스트 코드다. 먼저 테스트를 하기 위해 `targetUserId`로 `@BeforeEach` 어노테이션 블록에서 정의된
유저ID에 해당하는 유저에 대해 10,000 포인트를 충전한다. 왜냐하면 충전과 사용 액션이 거의 동시에 들어오기 때문에 사용
액션이 최초로 들어올 경우 0포인트일 경우 에러가 발생할 우려가 있기 때문이다.

위 처럼 전처리가 끝났다면, 테스트 시나리오를 작성한다. 먼저 구상한 로직은 입력으로 `long` 타입의 `amount` 값을 
받아들여 양수면 충전, 음수면 사용으로 받아들이고 `CompletableFuture` 블록 안에서 `CountDownLatch`를 사용해 대기시킨 후
동시에 실행시키기로 했다. 우선 `plan`이라는 long 배열 객체를 할당하고, 이 안에 충전하고자 하면 양수로, 사용하고자 하면
음수로 값을 입력해서 데이터를 컨트롤하기로 했다.

그럼 다음으로 `CountDownLatch`를 작업 대기 및 작업 완료 트리거로써 활용하기 위한 셋업을 한다. 우선 작업 대기 트리거로써
하나의 카운트를 가진 `standbyLatch` 객체 인스턴스와, 전체 작업의 개수를 가지고 있어 하나의 작업이 끝날 때 마다 하나씩
카운트를 줄이기 위해 `plan`의 요소 개수 만큼의 카운트를 가진 `latch` 객체 인스턴스를 준비한다.

그럼 이제 `plan`의 요소 개수 만큼의 `CompletableFuture`를 생성하기 위해 반복문을 실행한다. 여기서 `amount`로 넘어온
변수의 값이 양수면 충전을 수행하는 `Runnable`, 음수면 사용을 수행하는 `Runnable`을 할당하도록 처리한다. 그렇게 처리 후
`CompletableFuture.runAsync()` 메소드의 매개변수로 넘긴 람다함수에 `standbyLatch.await()` 메소드를 실행해서
`standbyLatch`의 count가 0이 될 때 까지 기다리도록 한다. 이후 로직은 외부에서 선언한 `Runnable`을 실행 후 `latch`의
카운트를 1 감소하도록 처리하는 것이다. 그렇게 반복문이 완료되고, `CompletableFuture.runAsync()` 메소드는 Awaitable하지
않고 실행 즉시 매개변수로 넘긴 람다함수를 수행하지만, `standbyLatch`의 count가 0이 될 때 까지 각 `CompletableFuture`
객체는 다음 `Runnable` 실행을 기다릴 것이다.

이제 반복문 이후에 있는 `standbyLatch.countDown()` 메소드를 실행하면 `CompletableFuture.runAsync()` 메소드의 매개변수로
넘긴 람다함수는 (다소의 차이는 있겠지만) 거의 동시에 실행될 것이다. 그리고 이번엔 `latch`의 count가 0이 될 때 까지
기다릴 수 있도록 `latch.await()` 메소드를 실행해 준다. 그럼 각 `CompletableFuture.runAsync()` 메소드가 종료될 때 까지
기다릴 것이다. 이렇게 테스트 로직 작성이 완료됐다.

이후 로직은 단순히 테스트에서 사용한 유저ID에 대한 유저 포인트를 꺼내서 `plan`의 합산과 비교해 보면 된다. 단, 최초에
사용 로직이 들어갈 시 포인트 부족 오류가 발생할 것을 우려해 추가한 10,000포인트의 존재는 잊지 말고 `plan`의 합산에
더해주기로 한다.

여기에 더해서 최종적으로 각 포인트 처리 작업들이 어떤 순서로 어떤 시간에 들어갔는지 궁금해져서 포인트 이력을 순서대로
`System.out.println()`으로 출력하는 로직도 하나 추가해 뒀다.

그럼 테스트 코드 작성이 완료된 것 같으니 테스트코드를 실행해 준다.

> &gt; Task :compileJava UP-TO-DATE<br>
 &gt; Task :processResources UP-TO-DATE<br>
 &gt; Task :classes UP-TO-DATE<br>
 &gt; Task :compileTestJava<br>
 &gt; Task :processTestResources NO-SOURCE<br>
 &gt; Task :testClasses<br>
 21:53:12.895 [Test worker] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [io.hhplus.tdd.point.integration.service.PointServiceIntegrationTest]: PointServiceIntegrationTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.<br>
 21:53:12.991 [Test worker] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration io.hhplus.tdd.TddApplication for test class io.hhplus.tdd.point.integration.service.PointServiceIntegrationTest<br><br>
 .   ____          _            __ _ _ <br>
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \ <br>
 ( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \ <br>
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) ) <br>
 '  |____| .__|_| |_|_| |_\__, | / / / / <br>
 =========|_|==============|___/=/_/_/_/ <br>
 :: Spring Boot ::                (v3.2.0) <br><br>
 2024-09-26T21:53:13.378+09:00  INFO 5143 --- [hhplus-tdd] [ &nbsp;&nbsp; Test worker] i.h.t.p.i.PointServiceIntegrationTest&nbsp;&nbsp;&nbsp; : Starting PointServiceIntegrationTest using Java 17.0.12 with PID 5143 (started by freddy in /Users/.../hhplus-tdd-java)<br>
 2024-09-26T21:53:13.379+09:00  INFO 5143 --- [hhplus-tdd] [ &nbsp;&nbsp; Test worker] i.h.t.p.i.PointServiceIntegrationTest&nbsp;&nbsp;&nbsp; : No active profile set, falling back to 1 default profile: "default"<br>
 2024-09-26T21:53:14.275+09:00  INFO 5143 --- [hhplus-tdd] [ &nbsp;&nbsp; Test worker] i.h.t.p.i.PointServiceIntegrationTest&nbsp;&nbsp;&nbsp; : Started PointServiceIntegrationTest in 1.159 seconds (process running for 2.18)<br>
 PointHistory[id=1, userId=-4680411755446274101, amount=10000, type=CHARGE, updateMillis=1727355195134]<br>
 PointHistory[id=2, userId=-4680411755446274101, amount=700, type=CHARGE, updateMillis=1727355195504]<br>
 PointHistory[id=3, userId=-4680411755446274101, amount=300, type=USE, updateMillis=1727355195850]<br>
 PointHistory[id=4, userId=-4680411755446274101, amount=950, type=CHARGE, updateMillis=1727355196022]<br>
 PointHistory[id=5, userId=-4680411755446274101, amount=1200, type=CHARGE, updateMillis=1727355196556]<br>
 PointHistory[id=6, userId=-4680411755446274101, amount=650, type=USE, updateMillis=1727355197036]<br>
 PointHistory[id=7, userId=-4680411755446274101, amount=200, type=USE, updateMillis=1727355197435]<br>
 PointHistory[id=8, userId=-4680411755446274101, amount=600, type=CHARGE, updateMillis=1727355197995]<br>
 PointHistory[id=9, userId=-4680411755446274101, amount=1100, type=USE, updateMillis=1727355198588]<br>
 OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended<br>
 &gt; Task :test<br>
 Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.<br>
 You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.<br>
 For more on this, please refer to https://docs.gradle.org/8.4/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.<br>
 BUILD SUCCESSFUL in 7s<br>
 4 actionable tasks: 2 executed, 2 up-to-date<br>
 오후 9:53:19: 실행이 완료되었습니다 ':test --tests "io.hhplus.tdd.point.integration.service.PointServiceIntegrationTest"'.

그럼 위 처럼 무난하게 통합 테스트 까지 통과된다.


## 🤦‍♂️ 과정 중 힘들었던 점

1. 본인은 원래 Python Django 엔지니어다. Django에는 존재하지 않은 DI(의존성 주입) 개념에 대해 이해하는 과정이 필요했다.
2. Mock 과 Stub에 대해 어중간하게 이해한 상태로 진행했을 때 Stubbing을 전혀 하지 않았으면서 테스트 코드가 왜 동작하지
 않는지 몇 시간을 직접 작성한 로직만 노려보고 있었다. 심지어 Java는 다뤄봤음에도 오래 전의 일이라 코드 문제라 생각한 게
 로직을 의심한 가장 큰 이유였다.
3. 테스트 코드 작성도 일종의 프레임워크이기 때문에 Given-When-Then 규칙을 알아도 어떤 기능이 있는 지 직접 검색하면서
 찾아볼 수 밖에 없었던 점이 테스트 코드 작성에 걸림돌이 되었다. 마치 400m 달리기를 하는데 장애물이 있어서 장애물 달리기
 를 하고 있는 심정이랄까.
4. 마찬가지로 단위 테스트와 통합 테스트라는 큰 틀이 존재한다는 건 알고 있었지만, 단위 테스트와 통합 테스트가 정확히
 어떻게 분리되는지 이해하는 데에 시간이 좀 걸렸다.
