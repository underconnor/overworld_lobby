# Overworld Lobby

오버월드 로비 서버용 보호 플러그인입니다. 서바이벌 상태에서 비행할 수 있게 하고, 시간·날씨·자연 스폰과 플레이어 행동을 설정에 따라 제한합니다. 상자는 기본으로 열고 사용할 수 있습니다.

## 설치

- **Minecraft Java / Paper 26.2, Java 25**. 기존 [Overworld Travel](https://github.com/underconnor/overworld_travel)과 같은 실행 환경입니다. [Paper 실행 환경 안내](https://docs.papermc.io/paper/getting-started/)
- 권한 관리는 해당 **Paper 백엔드에 설치한 LuckPerms**와 연동합니다. 프록시에만 LuckPerms를 설치하면 백엔드의 권한 검사를 처리하지 않습니다. [LuckPerms 설치 안내](https://luckperms.net/wiki/Installation)

1. [Releases](https://github.com/underconnor/overworld_lobby/releases)에서 `overworld_lobby-<버전>.jar`를 받습니다.
2. 로비 Paper 서버를 정상 종료하고 JAR를 그 서버의 `plugins/` 폴더에 넣습니다.
3. 서버를 시작합니다. 설정은 `plugins/OverworldLobby/config.yml`에 생성됩니다.
4. 설정을 수정한 뒤 `/lobby reload` 또는 콘솔의 `lobby reload`를 실행합니다.

처음 설치하면 **그 Paper 서버의 모든 월드**를 보호합니다. 일부 월드만 보호하려면 `worlds: [lobby]`처럼 실제 월드 이름을 지정하세요. WorldGuard를 함께 사용한다면 양쪽에서 허용한 행동만 가능합니다. 이 플러그인의 권한은 다른 플러그인이 취소한 이벤트를 강제로 허용하지 않습니다.

## 기본 동작

| 항목 | 기본값 |
| --- | --- |
| 게임 모드 | SURVIVAL |
| 비행 | 허용. 점프 키 두 번 또는 `/lobby fly` |
| 스폰 | `-4.5 63.5 -1.5`, yaw `180`, pitch `0` |
| 자동 스폰 이동 | 접속·리스폰·공허 추락 시 적용 |
| 시간 | 6000틱, 낮 고정 |
| 날씨 | CLEAR, 맑음 고정 |
| 자연 스폰 | 차단 |
| 블록 파괴·설치, 양동이 | 차단 |
| 문·트랩도어·버튼·레버·압력판·침대·제작대 등 | 차단 |
| 상자·함정 상자·엔더 상자·통·셜커 상자 | 열기와 안의 아이템 이동 허용 |
| 엔티티 조작·공격, PvP, 탈것 설치·탑승, 포털 | 차단 |
| 아이템 사용·소비·투사체·버리기·줍기 | 차단 |
| 플레이어 피해 | 환경 피해·PvP 모두 차단 |
| 허기·포화도 | 포만감 20, 포화도 20, 피로도 0 유지 |
| 폭발·불·유체 이동·성장·잎 소멸·눈/얼음 변화·몹 지형 파괴·피스톤·레드스톤 | 차단 |

상자에서 가져온 아이템에도 로비의 설치·사용·버리기 제한이 적용됩니다. 개인 인벤토리와 플러그인에서 여는 메뉴는 잠그지 않습니다. 일반 플러그인 텔레포트와 탈것에서 내리기도 허용합니다.

시간 고정은 월드 시계가 있는 차원에 적용합니다. 네더·엔드처럼 자체 시간이 고정된 차원도 보호하지만, 지원하지 않는 시간 변경 API는 호출하지 않습니다.

자연 스폰 차단은 기존 엔티티를 삭제하지 않습니다. 번식·스포너 등 자동 생성도 제한합니다. 명령어·다른 플러그인이 만드는 엔티티와 권한을 가진 플레이어의 생성은 별도 이벤트 규칙을 따릅니다.

## 차단 안내

사용자가 직접 시도한 행동을 막으면 `여기서는 블록을 부술 수 없습니다.` 같은 안내를 보냅니다. 기본은 빨간색 채팅이며 `[로비]` 접두어는 붙이지 않습니다. 같은 사용자의 안내는 기본 1초 간격으로 제한해 양손 클릭이나 연속 이벤트로 중복되지 않게 합니다.

`messages.enabled`로 전체 안내를 끄고, `messages.channel`을 `CHAT` 또는 `ACTION_BAR`로 선택할 수 있습니다. `messages.cooldown-ms`는 안내 간격이며 `prefix`는 기본 `&c`입니다. `messages.denied`의 행동별 문구를 빈 문자열로 설정하면 그 행동의 안내만 끕니다.

열 수 있는 상자와 권한으로 허용한 행동에는 차단 안내를 보내지 않습니다. 환경 피해·허기·포화도 유지와 자연 스폰·날씨 같은 자동 보호도 안내 없이 적용합니다.

## 명령어

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/lobby fly [on\|off]` | `overworld.lobby.fly` | 보호 월드에서 비행 켜기·끄기. 인수 없이 토글 |
| `/lobby reload` | `overworld.lobby.admin` | 파일을 검증한 뒤 설정 다시 적용 |
| `/spawn` | `overworld.lobby.spawn` | 지정한 로비 스폰으로 이동 |

`/overworldlobby` 별칭을 지원합니다. 명령어가 충돌하면 `/overworldlobby:lobby`, `/overworldlobby:spawn`을 사용하세요. `fly`와 `spawn`은 기본으로 모두에게 허용되고, `admin`은 기본 OP 권한입니다. 설정에서 비행을 꺼두면 `fly` 권한만으로 켤 수 없습니다.

## LuckPerms 권한 예외

Bukkit의 `Player.hasPermission`으로 검사하므로 LuckPerms의 그룹·유저·월드/서버 컨텍스트와 실시간 권한 변경을 사용합니다. LuckPerms가 없는 환경에서는 Bukkit 기본 권한을 사용합니다.

**OP는 기본적으로 블록·엔티티·아이템 등 행동 제한을 우회합니다.** OP도 설정한 서바이벌·비행·포만감·포화도·환경 피해 보호와 접속 시 스폰 이동은 적용받습니다. 이 동작들은 각각의 예외 권한으로 조정합니다.

| 권한 | 허용하는 행동 |
| --- | --- |
| `overworld.lobby.bypass` | 블록·엔티티·아이템 등 행동 제한 우회. OP 기본. 모드·스폰·허기·환경 피해 제외 |
| `overworld.lobby.bypass.mode` | 게임 모드·비행 강제 적용 우회 |
| `overworld.lobby.bypass.spawn` | 접속·리스폰·공허 추락 시 자동 스폰 이동 우회 |
| `overworld.lobby.bypass.block-break` | 블록 파괴 |
| `overworld.lobby.bypass.block-place` | 블록·장식·엔티티 설치 |
| `overworld.lobby.bypass.buckets` | 양동이 사용 |
| `overworld.lobby.bypass.interact` | 문·버튼·레버·제작대 등 블록 조작 |
| `overworld.lobby.bypass.containers` | `protection.containers: true`일 때 상자 사용 |
| `overworld.lobby.bypass.entity-interact` | 주민·액자·갑옷 거치대 등 엔티티 조작 |
| `overworld.lobby.bypass.entity-damage` | 플레이어 이외 엔티티 공격·장식 파괴 |
| `overworld.lobby.bypass.pvp` | 다른 플레이어 공격 |
| `overworld.lobby.bypass.item-use` | 아이템 사용·소비·투사체·낚시 |
| `overworld.lobby.bypass.item-drop` | 아이템 버리기 |
| `overworld.lobby.bypass.item-pickup` | 아이템·경험치 줍기 |
| `overworld.lobby.bypass.vehicles` | 탈것 설치·탑승 |
| `overworld.lobby.bypass.portals` | 포털 이동 |
| `overworld.lobby.bypass.player-damage` | 해당 플레이어가 환경 피해를 받을 수 있음 |
| `overworld.lobby.bypass.hunger` | 해당 플레이어의 허기가 감소할 수 있음 |

예를 들어 건축 담당 그룹에는 다음 권한을 줄 수 있습니다. `lp`는 로비 백엔드 콘솔에서 실행합니다.

```text
lp group builder permission set overworld.lobby.bypass.block-break true
lp group builder permission set overworld.lobby.bypass.block-place true
lp group builder permission set overworld.lobby.bypass.interact true
lp user <닉네임> permission set overworld.lobby.bypass true
```

OP의 기본 행동 우회를 끄려면 `lp user <닉네임> permission set overworld.lobby.bypass false`를 사용합니다. 개별 행동 권한을 `false`로 지정해도 전체 행동 우회가 `true`이면 해당 행동은 허용되므로, 일부 행동만 허용하려면 전체 우회를 끄고 필요한 개별 권한을 부여하세요.

크리에이티브 등 게임 모드를 직접 변경할 운영자는 `lp user <닉네임> permission set overworld.lobby.bypass.mode true`를 부여합니다. 접속·리스폰·공허 추락 시 자동 스폰 이동을 제외하려면 `lp user <닉네임> permission set overworld.lobby.bypass.spawn true`를 사용합니다. 이 권한들과 `bypass.hunger`, `bypass.player-damage`는 OP에게도 기본으로 부여하지 않습니다.

PvP는 공격자의 `bypass.pvp` 권한으로 허용합니다. 투사체를 사용하려면 `bypass.item-use`도 필요합니다. 피해자의 `bypass.player-damage`는 환경 피해 제한을 제어합니다.

행위자가 없는 자연 스폰·폭발·피스톤·시간·날씨 등은 권한으로 우회하지 않습니다. 해당 월드 환경 설정을 조정하세요. LuckPerms wildcard 권한을 사용하는 그룹은 실제 부여 결과를 확인하세요.

## 설정

전체 기본 설정은 [config.yml](src/main/resources/config.yml)에서 볼 수 있습니다.

```yaml
enabled: true
worlds: []
players:
  game-mode: SURVIVAL # SURVIVAL, ADVENTURE, CREATIVE, SPECTATOR
  allow-flight: true
  fly-speed: 0.1 # 0~1
  keep-food-full: true # 포만감·포화도 가득 유지
time:
  enabled: true
  ticks: 6000 # 0~23999
weather:
  enabled: true
  kind: CLEAR # CLEAR, RAIN, THUNDER
spawn:
  enabled: true
  world: '' # 보호 대상인 첫 번째 로드된 월드. 여러 월드는 이름을 명시하세요.
  x: -4.5
  y: 63.5
  z: -1.5
  yaw: 180.0
  pitch: 0.0
  on-join: true
  on-respawn: true
  void-rescue: true
prevent-natural-spawns: true
```

`protection`과 `environment` 아래의 각 키는 `true`일 때 차단합니다. `protection.containers`만 기본 `false`입니다. `allowed-containers` 목록의 상자만 일반 블록 조작 제한에서 예외로 취급합니다. 이 목록을 비우면 예외가 사라집니다. 제작대·화로 같은 블록을 목록에 추가할 수는 없습니다.

`players.keep-food-full`은 `protection.hunger`로 보호받는 플레이어의 포만감·포화도를 채웁니다. 환경 피해는 `protection.player-damage`, PvP는 `protection.pvp`로 제어합니다. 보호 범위를 벗어나면 플러그인이 채우기 전의 음식 상태를 복원합니다.

스폰은 소수 좌표와 시선을 그대로 사용합니다. `spawn.world`가 지정된 경우 이미 로드된 보호 월드여야 하며, 없는 월드를 새로 만들지 않습니다. 자동 이동은 보호받는 출발 월드에서 적용합니다. `bypass.spawn`은 자동 이동만 우회하므로 `/spawn`으로 직접 이동할 수 있습니다. 공허 구조는 월드 최소 높이보다 8블록 아래로 내려갈 때 적용합니다.

잘못된 타입·게임 모드·날씨·시간·속도·상자 이름은 거부합니다. 잘못된 파일로 `reload`하면 기존 설정을 유지합니다. 비행·게임 모드 적용 전 상태와 플러그인이 바꾼 월드 규칙은 보호 범위를 벗어나거나 정상 비활성화할 때 복원합니다. 월드 자체를 되돌리거나 기존 엔티티·아이템을 지우지는 않습니다.

## 빌드와 릴리즈

```bash
./gradlew build
```

Windows에서는 `gradlew.bat build`를 사용합니다. Java 25와 Gradle Wrapper를 사용하며 Paper API는 `26.2.build.129-stable`로 고정합니다. JAR는 `build/libs/overworld_lobby-<버전>.jar`에 생성됩니다. 다른 Overworld/Passport 플러그인을 먼저 빌드할 필요는 없습니다.

GitHub Actions는 `main` 푸시와 PR에서 테스트·빌드를 실행합니다. `build.gradle.kts` 버전과 일치하는 `v<버전>` 태그를 푸시하면 JAR, SHA-256 체크섬, 커밋 정보를 담은 `release-metadata.json`을 릴리즈에 올립니다.

## 검증

릴리즈 설명과 [검증 기록](docs/verification.md)에 자동 테스트·실제 Paper 실행 확인 범위와 남은 플레이 검증을 기록합니다.

## 라이선스

Copyright 2026 underconnor. [Apache License 2.0](LICENSE).
