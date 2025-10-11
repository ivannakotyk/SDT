package com.ivanka.audioeditor.client.core;
public interface Observable {
    void addObserver(Observer o);
    void removeObserver(Observer o);
    void notifyObservers();
}
