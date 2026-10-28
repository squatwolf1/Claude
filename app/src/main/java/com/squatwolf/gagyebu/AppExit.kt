package com.squatwolf.gagyebu

/**
 * 앱이 화면에서 사라지면 완전히 종료한다. 다만 공유 창·설치 화면처럼
 * 우리가 직접 띄운 다른 화면으로 잠깐 나갈 때는 종료하지 않도록 표시해 둔다.
 */
object AppExit {
    @Volatile
    var leavingForExternal = false
}
