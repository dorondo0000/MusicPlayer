# MusicPlayer

Cinema Mod의 브라우저 오디오 기능을 이용하는 Minecraft 서버 전체 음악 플레이어입니다.  
스크린 블록이나 WorldGuard 없이 단일 플러그인으로 동작하며, 관리 작업은 Spotify 스타일의 웹 UI에서 처리합니다.

## 주요 기능

- YouTube 음악 서버 전체 동기화 재생
- 재접속 시 현재 재생 위치부터 이어 듣기
- 웹 재생바를 이용한 전체 청취자 위치 이동
- 재생·일시정지·정지·이전·다음 곡 제어
- 웹에서 전체 재생 대기열 반복 켜기·끄기
- 현재 곡 처음으로 이동 후 다시 누르면 이전 곡으로 이동
- 흰색 진행률 BossBar 및 표시 여부 전환
- YAML 플레이리스트 저장과 공개 YouTube 플레이리스트 가져오기
- 관리자가 지정한 플레이리스트 이름 유지
- 플레이리스트 이름·곡·순서 편집
- 플레이리스트의 원하는 곡부터 재생
- 실제 재생 대기열을 별도 패널로 관리
- 플레이리스트와 최근 재생 곡을 대기열로 드래그 앤 드롭
- 최근 재생한 곡을 최대 30곡까지 자동 저장
- Skript 및 다른 플러그인에서 사용할 수 있는 정적 API
- 관리자 비밀번호 설정 명령어 제공

## 요구사항

- Paper 또는 Purpur 1.21 이상
- Java 21 이상
- 접속하는 플레이어의 클라이언트에 Cinema Mod

Cinema Mod 자체는 수정하지 않습니다. 버전이 고정된 전용 YouTube 브리지가 영상을 음소거 상태로 준비하고, 재생 위치를 적용한 뒤 소리를 시작합니다. 재생은 관리자 웹 포트나 서버 주소를 경유하지 않으며 각 클라이언트가 YouTube에 직접 연결합니다.

## 설치

1. [Releases](https://github.com/dorondo0000/MusicPlayer/releases)에서 최신 JAR을 받습니다.
2. JAR을 서버의 `plugins` 폴더에 넣습니다.
3. 서버를 완전히 재시작합니다.
4. 기본 웹 관리 화면은 `http://localhost:9090`에서 열 수 있습니다.
5. 콘솔 또는 OP가 `/musicplayer password <비밀번호>`로 웹 관리자 비밀번호를 설정합니다.

## 설정

`plugins/MusicPlayer/config.yml`

```yaml
web-server:
  enabled: true
  port: 9090

music:
  default-bossbar-enabled: true

security:
  # 최초 실행 시 비어 있으면 강력한 임의 비밀번호가 생성됩니다.
  admin-password: ""
```

## 웹 보안

웹 관리자 비밀번호는 서버 콘솔 또는 OP 권한으로 `/musicplayer password <비밀번호>`를 실행해 설정합니다. 관리자 화면과 모든 `/api/*` 요청은 유효한 로그인 세션이 있어야 접근할 수 있으며, 비밀번호를 바꾸면 기존 로그인은 모두 해제됩니다.

`config.yml`은 외부에 공개하지 마세요. 9090 포트를 신뢰할 수 없는 네트워크에 공개한다면 HTTPS 리버스 프록시를 사용해야 합니다. 음악 재생은 이 포트와 무관하므로 관리 화면을 외부에 공개할 필요가 없다면 방화벽에서 9090 포트를 닫아도 됩니다.

플레이리스트는 `plugins/MusicPlayer/playlists/*.yml`, 최근 재생 기록은 `plugins/MusicPlayer/recently-played.yml`에 저장됩니다.

## 플러그인 API

Kotlin:

```kotlin
val api = MusicPlayer.api

api.playTrack(
    "https://www.youtube.com/watch?v=...",
    title = "노래 제목",
    author = "아티스트"
)

// 플레이리스트의 세 번째 곡부터 재생
api.playPlaylist("playlist-name", shuffle = false, startIndex = 2)

api.addToQueue("https://www.youtube.com/watch?v=...", "노래 제목")
api.insertIntoQueue(1, track)
api.moveInQueue(2, 0)
api.removeFromQueue(1)

api.seekTo(60)
api.pauseMusic()
api.resumeMusic()
api.previousTrack()
api.nextTrack()
api.setLoop(true)
api.stopMusic()
api.toggleBossbar(false)

val currentTrack = api.getCurrentTrack()
val position = api.getPlaybackPositionSeconds()
val duration = api.getPlaybackDurationSeconds()
val queue = api.getQueue()
val recent = api.getRecentlyPlayed()
```

Java 또는 `skript-reflect`에서는 `MusicPlayer.getApi()`로 같은 API에 접근할 수 있습니다.

## 빌드

Windows:

```powershell
.\gradlew.bat shadowJar
```

완성된 JAR은 `build/libs/CinematoMusicPlayer-1.1.0.jar`에 생성됩니다.

## Cinema Mod의 R 키

R 키 화면은 Cinema Mod 클라이언트가 자체 등록하는 기능이라 서버 플러그인만으로 키 입력을 제거할 수 없습니다. 필요하면 Minecraft 키 설정에서 Cinema Mod의 비디오 대기열 키를 해제하세요.
