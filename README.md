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
| 게임 모드 | 일반 플레이어 SURVIVAL. OP·전체 우회 권한 사용자는 접속 시 CREATIVE |
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
| 허기·포화도 | OP·우회 사용자도 포만감 20, 포화도 20, 피로도 0 유지 |
| 낙하 피해 | OP·우회 사용자도 차단 |
| 월드 경계 | 기본 미설정. 코너 두 개로 X/Z 사각 경계를 지정하면 OP·우회 사용자도 적용 |
| 폭발·불·유체 이동·성장·잎 소멸·눈/얼음 변화·몹 지형 파괴·피스톤·레드스톤 | 차단 |

상자에서 가져온 아이템에도 로비의 설치·사용·버리기 제한이 적용됩니다. 개인 인벤토리와 플러그인에서 여는 메뉴는 잠그지 않습니다. 일반 플러그인 텔레포트와 탈것에서 내리기도 허용합니다.

시간 고정은 월드 시계가 있는 차원에 적용합니다. 네더·엔드처럼 자체 시간이 고정된 차원도 보호하지만, 지원하지 않는 시간 변경 API는 호출하지 않습니다.

Citizens NPC 우클릭은 일반 사용자도 허용하며 엔티티 조작 차단 안내를 표시하지 않습니다. Citizens가 활성 상태에서 직접 등록한 NPC 메타데이터를 확인합니다. NPC 공격·물리적 변경과 일반 엔티티 조작은 기존 보호 규칙을 따르며, 다른 플러그인이 취소한 클릭은 그대로 유지합니다.

자연 스폰 차단은 기존 엔티티를 삭제하지 않습니다. 번식·스포너 등 자동 생성도 제한합니다. 명령어·다른 플러그인이 만드는 엔티티와 권한을 가진 플레이어의 생성은 별도 이벤트 규칙을 따릅니다.

## 차단 안내

사용자가 직접 시도한 행동을 막으면 `여기서는 블록을 부술 수 없습니다.` 같은 안내를 보냅니다. 기본은 빨간색 채팅이며 `[로비]` 접두어는 붙이지 않습니다. 같은 사용자의 안내는 기본 1초 간격으로 제한해 양손 클릭이나 연속 이벤트로 중복되지 않게 합니다.

`messages.enabled`로 전체 안내를 끄고, `messages.channel`을 `CHAT` 또는 `ACTION_BAR`로 선택할 수 있습니다. `messages.cooldown-ms`는 안내 간격이며 `prefix`는 기본 `&c`입니다. `messages.denied`의 행동별 문구를 빈 문자열로 설정하면 그 행동의 안내만 끕니다.

열 수 있는 상자와 권한으로 허용한 행동에는 차단 안내를 보내지 않습니다. 환경 피해·허기·포화도 유지와 자연 스폰·날씨 같은 자동 보호도 안내 없이 적용합니다.

## 명령어

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/lobby fly [on\|off]` | `overworld.lobby.fly` | 보호 월드에서 비행 켜기·끄기. 인수 없이 토글 |
| `/lobby time <day\|night\|noon\|midnight\|0..23999\|off\|default> [월드]` | OP 또는 `overworld.lobby.admin` | 월드별 시간을 바로 적용하고 저장 |
| `/lobby weather <clear\|rain\|thunder\|off\|default> [월드]` | OP 또는 `overworld.lobby.admin` | 월드별 날씨를 바로 적용하고 저장 |
| `/lobby reload` | `overworld.lobby.admin` | 파일을 검증한 뒤 설정 다시 적용 |
| `/lobby status` | `overworld.lobby.admin` | 현재 접속자의 OP·전체 우회·게임 모드 적용 현황 확인 |
| `/setspawn` 또는 `/lobby setspawn` | OP 또는 `overworld.lobby.admin` | 현재 위치·시선으로 기본 로비 스폰 저장 |
| `/lobby setspawn <월드> <x> <y> <z> [yaw] [pitch]` | OP 또는 `overworld.lobby.admin` | 지정한 월드·정확한 좌표로 기본 스폰 저장. 콘솔 사용 가능 |
| `/lobby border set <x1> <z1> <x2> <z2> [월드]` | OP 또는 `overworld.lobby.admin` | 두 코너로 보이지 않는 사각 경계 저장 |
| `/lobby border pos1\|pos2 [x z] [월드]` | OP 또는 `overworld.lobby.admin` | 첫째·둘째 코너 선택. 좌표를 생략하면 현재 위치 사용; pos2에서 저장 |
| `/lobby border off\|info [월드]` | OP 또는 `overworld.lobby.admin` | 해당 월드의 경계 해제·설정 조회 |

`/overworldlobby` 별칭을 지원합니다. 명령어가 충돌하면 `/overworldlobby:lobby`, `/overworldlobby:spawn`을 사용하세요. `fly`와 `spawn`은 기본으로 모두에게 허용되고, `admin`은 기본 OP 권한입니다. 일반 사용자는 설정에서 비행을 꺼두면 `fly` 권한만으로 켤 수 없습니다. OP·전체 우회 사용자는 설정과 `fly` 권한에 관계없이 비행 명령을 사용할 수 있습니다.

게임 안에서 월드를 생략하면 현재 있는 월드를 설정합니다. 콘솔에서는 월드 이름을 반드시 지정합니다. 보호 대상으로 이미 로드된 월드만 설정하며, 다른 월드의 시간·날씨는 유지합니다. `off`는 그 월드의 해당 고정을 해제해 원래 진행 상태를 복원하고, `default`는 해당 월드의 예외를 지워 공통 설정을 다시 따릅니다. 시간과 날씨는 각각 독립적으로 설정합니다. 변경은 즉시 적용하고 `config.yml`에 저장하므로 서버 재시작 후에도 유지됩니다.

```text
/lobby time day lobby
/lobby time 18000 lobby
/lobby weather rain lobby
/lobby time off lobby
/lobby weather default lobby
```

`day`는 1000틱, `noon`은 6000틱, `night`는 13000틱, `midnight`는 18000틱입니다. 네더·엔드 등 시계가 고정된 차원에는 시간을 새로 고정할 수 없으며, 네더·엔드에는 날씨를 고정할 수 없습니다. 그 월드의 `off`·`default` 설정은 사용할 수 있습니다.

스폰으로 지정할 위치에 서서 `/setspawn`을 실행하면 소수 좌표와 시선까지 그대로 저장합니다. `/lobby setspawn lobby -4.5 63.5 -1.5 180 0`처럼 좌표를 직접 지정할 수도 있습니다. 생략한 시선은 플레이어의 현재 시선, 콘솔에서는 `0, 0`을 사용합니다. 이미 로드된 보호 월드만 지정하며, 변경한 목적지는 자동 접속·리스폰·공허 이동에 바로 적용되고 서버 재시작 후에도 유지됩니다. 자동 이동을 켜고 끄는 기존 설정은 유지합니다. 이 명령은 사용자를 바로 이동시키거나 Minecraft·Multiverse의 월드 스폰을 바꾸지 않습니다.

수동 `/spawn`과 `/overworldlobby:spawn` 명령은 등록하지 않습니다. OP·우회·기존 스폰 권한이 있어도 사용할 수 없습니다. 관리자 `/setspawn`과 자동 접속·리스폰·공허 복구는 유지합니다.

경계는 월드마다 따로 저장하며 높이 제한 없이 X/Z 좌표로 검사합니다. 코너 순서는 상관없고 경계 좌표 자체는 포함합니다. 도보·비행·텔레포트·탈것으로 경계를 넘을 수 없으며, 거부하면 승인된 `이 월드의 경계를 벗어날 수 없습니다.` 안내를 보냅니다. 시각 효과나 블록을 만들지 않습니다. OP·전체 우회 권한에도 동일하게 적용합니다. 스폰과 경계를 같은 월드에 설정할 때는 스폰이 경계 안에 있어야 합니다.

```text
/lobby border set -100 -50 100 50 lobby
/lobby border pos1 -100 -50 lobby
/lobby border pos2 100 50 lobby
/lobby border info lobby
/lobby border off lobby
```

## LuckPerms 권한 예외

Bukkit의 `Player.hasPermission`으로 검사하므로 LuckPerms의 그룹·유저·월드/서버 컨텍스트와 실시간 권한 변경을 사용합니다. LuckPerms가 없는 환경에서는 Bukkit 기본 권한을 사용합니다.

**OP와 `overworld.lobby.bypass` 사용자는 플레이어 행동·일반 피해·모드·비행 제한을 우회합니다.** 포만감·포화도 유지, 허기·피로도 차단, 낙하 피해 방지, 월드 경계와 자동 스폰 이동은 OP·우회 사용자에게도 적용됩니다. OP 여부를 직접 확인하므로 LuckPerms가 전체 우회 노드를 `false`로 반환해도 행동 우회가 적용됩니다. OP의 행동을 제한하려면 OP를 해제한 뒤 필요한 개별 권한만 부여하세요.

OP와 전체 우회 사용자는 접속 시 스폰 이동이 끝난 뒤 크리에이티브로 시작합니다. 이후 직접 선택한 게임 모드는 유지하며, 주기적으로 크리에이티브로 되돌리지 않습니다. 접속 중 전체 우회 권한을 새로 받으면 기존 보호 상태를 복원한 뒤 한 번 크리에이티브를 적용합니다. 스폰 이동은 권한으로 우회하지 않습니다.

| 권한 | 허용하는 행동 |
| --- | --- |
| `overworld.lobby.bypass` | 행동·일반 피해·모드·비행 우회, 접속 시 크리에이티브. 허기·낙하·경계·스폰 보호 적용 |
| `overworld.lobby.bypass.mode` | 게임 모드·비행 강제 적용 우회 |
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
| `overworld.lobby.bypass.player-damage` | 낙하 이외의 환경 피해를 받을 수 있음 |

예를 들어 건축 담당 그룹에는 다음 권한을 줄 수 있습니다. `lp`는 로비 백엔드 콘솔에서 실행합니다.

```text
lp group builder permission set overworld.lobby.bypass.block-break true
lp group builder permission set overworld.lobby.bypass.block-place true
lp group builder permission set overworld.lobby.bypass.interact true
lp user <닉네임> permission set overworld.lobby.bypass true
```

개별 행동 권한을 `false`로 지정해도 OP 또는 전체 우회 권한이 있으면 해당 행동을 허용합니다. 일부 행동만 허용하려면 OP를 해제하고 전체 우회 권한을 끈 뒤 필요한 개별 권한만 부여하세요.

전체 우회 없이 게임 모드와 비행만 직접 변경하려면 `lp user <닉네임> permission set overworld.lobby.bypass.mode true`를 부여합니다. 낙하 이외의 환경 피해는 `bypass.player-damage`로 우회합니다. 기존 `bypass.hunger` 권한은 더 이상 사용하지 않으며, 이미 부여돼 있어도 허기·포화도 보호를 해제하지 않습니다. 허기·낙하·경계·자동 스폰 보호를 제외하는 권한은 없습니다.

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
world-settings:
  lobby:
    time:
      enabled: true
      ticks: 6000
    weather:
      enabled: true
      kind: CLEAR
  another-world:
    time:
      enabled: false # 이 월드의 시간은 자연 진행
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

최상위 `time`·`weather`는 공통 기본값이고, `world-settings`는 월드 이름별 예외입니다. 예외에서 빠진 항목은 공통 값을 따릅니다. 이 목록은 보호 대상 `worlds`를 늘리거나 월드를 새로 생성하지 않습니다. 기존 설정에 `world-settings`가 없어도 이전처럼 공통 설정을 적용합니다.

`protection`과 `environment` 아래의 각 키는 `true`일 때 차단합니다. `protection.containers`만 기본 `false`입니다. `allowed-containers` 목록의 상자만 일반 블록 조작 제한에서 예외로 취급합니다. 이 목록을 비우면 예외가 사라집니다. 제작대·화로 같은 블록을 목록에 추가할 수는 없습니다.

`players.keep-food-full`과 `protection.hunger`가 켜져 있으면 OP·우회 사용자를 포함한 모든 보호 월드 플레이어의 포만감·포화도를 채웁니다. `protection.player-damage`는 환경 피해를 제어하며 낙하 피해는 우회 권한과 관계없이 차단합니다. PvP는 `protection.pvp`로 제어합니다. 해당 설정을 끄거나 보호 범위를 벗어나면 음식 유지가 해제되고 플러그인이 채우기 전의 음식 상태를 복원합니다.

스폰은 소수 좌표와 시선을 그대로 사용합니다. `spawn.world`가 지정된 경우 이미 로드된 보호 월드여야 하며, 없는 월드를 새로 만들지 않습니다. 자동 이동은 보호받는 출발 월드에서 OP·전체 우회 사용자를 포함해 적용합니다. 접속·리스폰·공허 구조는 각각의 설정으로만 끌 수 있고 권한으로 우회하지 않습니다. 공허 구조는 월드 최소 높이보다 8블록 아래로 내려갈 때 적용합니다.

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
