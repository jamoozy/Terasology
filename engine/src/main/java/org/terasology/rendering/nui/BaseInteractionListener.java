/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.nui;

import org.terasology.input.MouseInput;
import org.terasology.math.Vector2i;

/**
 * @author Immortius
 */
public class BaseInteractionListener implements InteractionListener {

    protected FocusManager focusManager;
    private boolean mouseOver;

    @Override
    public void setFocusManager(FocusManager focusManager) {
        this.focusManager = focusManager;
    }

    @Override
    public void onMouseOver(Vector2i pos, boolean topMostElement) {
        this.mouseOver = topMostElement;
    }

    @Override
    public void onMouseLeave() {
        this.mouseOver = false;
    }

    @Override
    public boolean onMouseClick(MouseInput button, Vector2i pos) {
        return false;
    }

    @Override
    public boolean onMouseDoubleClick(MouseInput button, Vector2i pos) {
        return onMouseClick(button, pos);
    }

    @Override
    public void onMouseDrag(Vector2i pos) {

    }

    @Override
    public void onMouseRelease(MouseInput button, Vector2i pos) {
    }

    @Override
    public boolean onMouseWheel(int wheelTurns, Vector2i pos) {
        return false;
    }

    @Override
    public boolean isMouseOver() {
        return mouseOver;
    }
}
