# FlyffU WebView Client

A lightweight WebViewClient for playing Flyff Universe on Android. It allows you to play with multiple clients and access useful utilities.

## Features

*   **Immersive Fullscreen**: Enjoy a truly immersive experience with the application running in full screen.
*   **Movable Floating Action Button (FAB)**: The main FAB can be dragged and repositioned anywhere on the screen for convenience.
*   **Multiple Clients**: Open and manage several game instances simultaneously.
*   **Quick Switching**: Easily switch between open clients with a tap on the floating action button (FAB).
*   **Rename Clients**: Customize the names of your clients for better organization.
*   **Delete Clients**: Remove clients and their saved data when you no longer need them.
*   **Utilities Menu**: Quickly access useful websites suchs as:
    *   Flyff Wiki
    *   Madrigal Inside
    *   Flyffulator
*   **Data Persistence**: Each client's data (including action button configurations) is saved individually, allowing you to continue where you left off.
*   **Action Buttons (ABs)**: Create custom on-screen buttons for various actions.
    *   **Normal Buttons**: Dispatch a single key press (e.g., 'F1', '1', 'A').
    *   **Macro Buttons**: Execute a sequence of key presses with a customizable delay between each key (e.g., "1,2,3").
    *   **Timed Repeat Macro Buttons**: Toggle continuous repetition of a single key press at a user-defined interval.
*   **Hide/Show Action Buttons**: A dedicated button will appear on the screen if any action buttons are created. Tap this button to toggle the visibility of all action buttons. Its state (hidden/shown) is automatically saved.

## How to Use

1.  **Installation**: Download and install the latest APK.
2.  **Immersive Fullscreen**: The application automatically enters immersive fullscreen mode upon launch.
3.  **Movable FABs**: Both the main FAB and action buttons (including the hide/show button) can be dragged and repositioned anywhere on the screen. Their positions are automatically saved.
4.  **Open/Create Clients**:
    *   Upon launch, a default client will be opened.
    *   To create a new client, long-press the main Floating Action Button (FAB).
    *   In the menu that appears, select "New Client".
5.  **Switch Between Clients**:
    *   Tap the main FAB to switch to the next open client.
6.  **Manage Clients (Long-press the main FAB)**:
    *   **Clients**:
        *   **Switch to**: Switch to a specific client.
        *   **Kill**: Close an open client (data is retained).
        *   **Open**: Open a saved client that is not currently active.
        *   **Rename**: Change a client's name.
        *   **Delete**: Permanently remove a client and all its data.
    *   **Action Buttons**: 
        *   **New**: Create a new action button. You can choose from:
            *   **Function Key**: Assigns a standard function key (F1-F12).
            *   **Custom Key**: Assigns a single custom character key.
            *   **Macro**: Defines a sequence of keys (e.g., "1,2,3") with a delay.
            *   **Timed Repeat Macro**: Sets a single key to repeat at a specified interval, toggled on/off.
        *   **Color**: Change the color of an existing action button or all buttons for the selected client.
        *   **Delete**: Remove an existing action button.
    *   **Utils**:
        *   **Flyff Wiki**: Opens the official Flyff wiki.
        *   **Madrigal Inside**: Opens the Madrigal Inside website.
        *   **Flyffulator**: Opens the Flyffulator.
        *   To close a utility, click the corresponding menu item again and confirm closure.
7.  **Hide/Show Action Buttons**:
    *   A dedicated hide/show button will appear on the screen if any action buttons are created.
    *   Tap this button to toggle the visibility of all action buttons.
    *   The state (hidden/shown) is automatically saved across app restarts.
8.  **Exit Application**: Press your device's "Back" button twice to exit.