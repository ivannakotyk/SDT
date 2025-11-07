package com.ivanka.audioeditor.client.core.observers;

import com.ivanka.audioeditor.client.core.Observer;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.mediator.EditorMediator;

/**
 * Єдиний спостерігач-місток: усі EditorEvent потрапляють у медіатор рівно ОДИН раз.
 */
public class MediatorObserver implements Observer {
    private final EditorMediator mediator;
    public MediatorObserver(EditorMediator mediator) { this.mediator = mediator; }
    @Override public void update(EditorEvent e) { mediator.onEvent(null, e); }
}
