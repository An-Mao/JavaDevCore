package anmao.dev.core.system;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

public class KeySimulate {
    public static void simulateKeyPress(int keyCode) {
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");

        // 模拟按下按键
        input.input.ki.wVk = new WinDef.WORD(keyCode);
        input.input.ki.dwFlags = new WinDef.DWORD(0); // 0 表示按下按键
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), (WinUser.INPUT[]) input.toArray(1), input.size());
    }

    public static void simulateKeyRelease(int keyCode) {
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");

        // 模拟释放按键
        input.input.ki.wVk = new WinDef.WORD(keyCode);
        input.input.ki.dwFlags = new WinDef.DWORD(WinUser.KEYBDINPUT.KEYEVENTF_KEYUP); // KEYEVENTF_KEYUP 表示释放按键
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), (WinUser.INPUT[]) input.toArray(1), input.size());
    }

    public interface User32 extends com.sun.jna.platform.win32.User32 {
        User32 INSTANCE = Native.load("user32", User32.class);
    }
}
