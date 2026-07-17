题：什么是 HashMap？
答：HashMap 是 Java 中常用的 Map 集合实现，JDK8 底层采用数组 + 链表 + 红黑树的数据结构，通过 hash 算法定位元素位置，平均查询时间复杂度为 O(1)。

题：HashMap 为什么线程不安全？
答：HashMap 线程不安全主要体现在多线程 put 时可能发生数据覆盖，以及扩容过程中可能出现链表结构异常。并发环境下建议使用 ConcurrentHashMap。

题：ConcurrentHashMap 如何保证线程安全？
答：JDK8 中 ConcurrentHashMap 通过 CAS + synchronized 实现线程安全。空桶插入使用 CAS，存在冲突时锁住当前桶节点，实现更细粒度的并发控制。

题：ArrayList 和 LinkedList 的区别？
答：ArrayList 底层基于数组实现，查询快，插入删除慢；LinkedList 底层基于双向链表实现，插入删除效率高，但随机访问效率低。

题：Java 中 HashMap 的扩容机制是什么？
答：HashMap 默认初始容量为16，负载因子为0.75，当元素数量超过容量乘以负载因子时触发扩容，扩容后容量变为原来的两倍。

题：什么是 volatile？
答：volatile 是 Java 提供的轻量级同步机制，可以保证变量的可见性，并禁止指令重排序，但不能保证操作的原子性。

题：volatile 为什么不能保证原子性？
答：因为 volatile 只能保证读写操作的可见性，例如 count++ 包含读取、加1、写回三个步骤，多个线程同时执行仍然可能出现数据覆盖。

题：synchronized 和 Lock 的区别？
答：synchronized 是 Java 关键字，由 JVM 实现；Lock 是 Java 类库提供的接口，具有更灵活的锁控制能力，例如可中断锁、公平锁、多个条件队列。

题：Java 创建线程有哪些方式？
答：Java 创建线程主要有四种方式：继承 Thread、实现 Runnable、实现 Callable、使用线程池。实际开发推荐使用线程池管理线程。

题：线程池的核心参数有哪些？
答：ThreadPoolExecutor 有七个核心参数：corePoolSize、maximumPoolSize、keepAliveTime、TimeUnit、BlockingQueue、ThreadFactory、RejectedExecutionHandler。

题：线程池执行任务流程是什么？
答：提交任务后，如果线程数小于核心线程数创建核心线程；核心线程满后任务进入阻塞队列；队列满后创建非核心线程；超过最大线程数执行拒绝策略。

题：线程池有哪些拒绝策略？
答：Java 默认提供四种拒绝策略：AbortPolicy（抛异常）、CallerRunsPolicy（调用者执行）、DiscardPolicy（丢弃任务）、DiscardOldestPolicy（丢弃最老任务）。

题：Spring IOC 是什么？
答：IOC 即控制反转，将对象的创建和依赖关系管理交给 Spring 容器完成，降低对象之间的耦合，提高代码可维护性。

题：Spring Bean 生命周期是什么？
答：Spring Bean 生命周期包括实例化、属性注入、Aware接口处理、BeanPostProcessor前置处理、初始化方法、BeanPostProcessor后置处理、Bean销毁。

题：Spring AOP 的实现原理是什么？
答：Spring AOP 主要通过动态代理实现，包括 JDK 动态代理和 CGLIB 代理。通过代理对象在方法执行前后织入增强逻辑。

题：Spring 事务传播行为有哪些？
答：Spring 事务传播行为主要包括 REQUIRED、REQUIRES_NEW、SUPPORTS、NOT_SUPPORTED、MANDATORY、NEVER、NESTED。其中 REQUIRED 是默认传播行为。

题：Spring 事务为什么会失效？
答：常见原因包括方法不是 public、内部方法调用导致代理失效、异常被捕获没有抛出、异常类型不是 RuntimeException、没有开启事务管理。

题：MySQL 为什么使用 B+Tree 索引？
答：B+Tree 高度较低，可以减少磁盘IO，同时叶子节点通过链表连接，适合范围查询，并且非叶子节点只存索引，提高空间利用率。

题：MySQL 索引失效的情况有哪些？
答：常见情况包括索引列使用函数、隐式类型转换、使用前导模糊查询 like '%xxx'、联合索引不满足最左匹配原则。

题：MySQL 事务隔离级别有哪些？
答：MySQL 支持四种事务隔离级别：读未提交、读已提交、可重复读、串行化。MySQL 默认隔离级别是可重复读。

题：MySQL 如何解决幻读？
答：MySQL InnoDB 通过 MVCC 和 Next-Key Lock 机制解决幻读问题，MVCC 控制数据版本，Next-Key Lock 防止范围数据变化。

题：Redis 为什么速度快？
答：Redis 基于内存操作，同时采用高效的数据结构，并通过 IO 多路复用提高网络处理能力，因此具有很高的读写性能。

题：Redis 缓存穿透如何解决？
答：缓存穿透可以通过布隆过滤器过滤不存在的数据，或者缓存空对象避免大量请求直接访问数据库。

题：Redis 缓存击穿如何解决？
答：缓存击穿通常发生在热点 Key 过期时，可以通过分布式锁控制数据库访问，或者设置热点 Key 永不过期。

题：Redis 缓存雪崩如何解决？
答：缓存雪崩是大量 Key 同时过期导致数据库压力增加，可以通过设置随机过期时间、多级缓存、限流等方式解决。

题：Kafka 如何保证消息不丢失？
答：Kafka 通过生产者 ack=all、消息重试机制、副本同步机制以及消费者手动提交 offset 来保证消息可靠性。

题：RabbitMQ 如何保证消息不丢失？
答：RabbitMQ 通过生产者确认机制、消息持久化、队列持久化、消费者手动 ACK 等方式保证消息可靠传递。

题：什么是 JVM？
答：JVM 是 Java 虚拟机，负责执行 Java 字节码，实现 Java 程序跨平台运行，同时负责内存管理和垃圾回收。

题：JVM 内存区域有哪些？
答：JVM 内存主要包括程序计数器、虚拟机栈、本地方法栈、堆、方法区。其中堆是对象主要存放区域。

题：JVM 垃圾回收算法有哪些？
答：常见垃圾回收算法包括标记清除、复制算法、标记整理、分代收集算法。

题：什么情况下会发生 OOM？
答：常见原因包括堆内存不足、大量对象无法回收、内存泄漏、线程创建过多、直接内存不足等。