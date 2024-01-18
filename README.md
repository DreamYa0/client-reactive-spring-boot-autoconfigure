# 使用示例

## spring cloud eureka注册接口响应式调用示例

调用接口定义

```java
@ReactiveFeignClient(name = "web-flux-app")
public interface GreetingReactive {

    @GetMapping("/greeting")
    Mono<String> greeting();

    @GetMapping("/greetingWithParam")
    Mono<String> greetingWithParam(@RequestParam(value = "id") Long id);
}
```

spring boot application 启动类上添加 @EnableEurekaClient 和 @EnableReactiveFeignClients(basePackages = "com.ntocc.**.integrate.client") 注解，注解中的 basePackages 路径为 @ReactiveFeignClient接口的所在路径。

其他更详细的用法请参考 feign客户端的使用文档

## 普通Http接口响应式调用示例

```java
@Autowired
private ReactiveRest reactiveRest;

@Test
    void testPayList() {
final PayListReq.Data data = new PayListReq.Data();
        data.setBaseCode("tj");
        data.setToken("05d81d1e5e2d152c6414b47a95781b17");
        data.setPageNum(1);
        data.setPageSize(20);
        data.setCanOperate(false);
        data.setDesc(true);
        data.setOrgcode(Lists.newArrayList("200MQT032H"));
        data.setPayFlowStatus(Lists.newArrayList(0,1));
        data.setPayComplete(Lists.newArrayList(0, 2));
        data.setStartCreateTime(1635305004000L);
        data.setEndCreateTime(1640689830704L);

final PayListReq req = new PayListReq(data);

final Mono<String> mono = reactiveRest.post("http://ktor.stress.chinawayltd.com/waybill/pay/list/payplan",
        req);
        System.out.println(mono.block());
        }
```

## dubbo接口响应式调用示例

```java
@Component
public class LoginUserInfoProxy {

    @Reference(async = true)
    private LoginUserInfoService loginUserInfoService;

    public Mono<UserAllInfoResp> getLoginUserInfo4PC(String token) {
        return ReactiveRpc.call(
                () -> loginUserInfoService.getLoginUserInfo4PC(RpcRequestWrapper.wrapper(token)));
    }
}
```

