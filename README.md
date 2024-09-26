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
> 둘째, 셋째 요청이 어느 순서로 처리될지는 보장하지 않는다. 그 이유는 **JVM 스케줄링**에 따라 스레드가 실행되기
> 때문인데, 어떤 스레드가 먼저 `synchronized` 블록에 진입할지는 **운영체제**나 **JVM**의 스케줄러에 의존하기 때문에
> **대기 중인 스레드 간의 순서**는 보장되지 않는 것이 특징이다.

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
> 즉, 객체의 메모리 주소가 다르더라도, 두 객체가 동일한 `equals` 결과를 반환하고, 같은 `hashCode` 결과값을 가지고
> 있다면 동일한 키로 인식된다.

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

이러면 정상 처럼 보인다. 하지만 이번주 멘토링을 통해 얻은 키워드 중 `ConcurrentHashMap`이라는 클래스가 있었다.
이 클래스에 대해 알아보니 동기처리를 보장하는데, 무려 그 범위가 키 단위라고 한다. 그렇다면 `ConcurrentHashMap`으로
처리하는 편이 성능적으로 훨씬 이점이 많다.

```java
@Component
public final class VirtualTransaction {

    private final Map<String, Lock> locks = new ConcurrentHashMap<>();
  
    private Lock getLock(String scope) {
        return locks.computeIfAbsent(scope, k -> new ReentrantLock(true));
    }
  
    // ... 생략 ...
}
```

이러면 `WeakHashMap`의 이점은 사라지지만, 키를 정리하는 로직 까지 직접 구현하기엔 시간이 모자라니 이 정도로 정리하는
편이 좋아 보인다.
