package javawin;

import java.awt.Component;

// То, что программа может сообщить своему окну.
public interface AppBody {
    // окно создано — можно запомнить его (например, чтобы менять заголовок)
    default void attached(Win w) {}

    // окно закрывается; вернуть true, если программа сама вызовет doClose (например, после вопроса «Сохранить?»)
    default boolean beforeClose(Runnable doClose) { return false; }

    // окно закрыто — остановить таймеры
    default void closing() {}

    // что получает фокус при активации окна
    default Component focusTarget() { return null; }
}
