/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.car.app.host;

import androidx.annotation.NonNull;

/**
 * A host-side interface for reporting to clients that an item was selected.
 */
public interface OnSelectedListenerWrapper {
    /**
     * Notifies that an item was selected.
     *
     * <p>This event is called even if the selection did not change, for example, if the user
     * selected an already selected item.
     */
    void onSelected(int selectedIndex, @NonNull OnDoneCallback callback);
}