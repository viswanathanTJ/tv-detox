# TV Monitor: screen design

**Who it is for.** Someone sitting on the sofa about 3 metres from the TV, holding the remote. They are not technical. They want to know if the TV is slow because it is full, and how to fix it. They only have arrows and OK.

**Layout** (one page that scrolls with the arrows, plus a message bar that stays at the bottom):

```
 TV Monitor                                      Updates every 5 seconds
 +----------------------------------------------------------------------+
 |  Your TV is a bit full                       (amber; green or red)   |
 |  Free up memory to speed it up.                                      |
 |  [#################-------------]  Memory in use                     |
 |  Free memory: 640 MB of 2,000 MB                                     |
 +----------------------------------------------------------------------+
 [  Free up memory  ]  [ Refresh ]       <- first focus when it opens
 Apps you use
  [icon] Netflix                          412 MB          [ Stop ]
  [icon] YouTube                          230 MB          [ Stop ]
 Other apps running in the background
  [icon] Weather service                   96 MB          [ Stop ]
 Needed by your TV
  Your TV needs these. Do not stop them.   [ Show them (24) ]
 ======================================================================
  Use the arrows to move. Press OK to choose.          (message bar)
```

**The 3 most important actions.** 1) Free up memory (one big button, focused on open). 2) Stop one app (Stop button on its row). 3) Refresh.

**What a beginner sees first.** One coloured sentence that says how the TV is doing, a bar, the free memory in MB, and the big "Free up memory" button already highlighted. Pressing OK once is the right thing to do.

**Status words.**
- *Running well* (green): a quarter or more of the memory is free. Nothing to do.
- *A bit full* (amber): 12 to 25 percent free. Apps may open slowly; Free up memory helps.
- *Very full* (red): under 12 percent free. The TV may lag or close apps; free up memory or stop apps you are not using.
- *Free memory*: memory the TV can hand to a new app right now.
- *Needed by the TV*: parts of the TV system. They have no Stop button.

**Confirm before Stop.** On a remote, OK is pressed often and fast, and the Stop button sits under the focus while the user scrolls. So the first OK only turns the button amber and changes it to "Stop Netflix? Press OK again to confirm". A second OK within 5 seconds stops the app. Moving away, or waiting 5 seconds, cancels it. While a Stop is waiting for its second press the screen does not refresh, so the button is not reset under the user's thumb. Stopping an app is safe (it only closes it, like a restart would), but it loses what was on screen in that app, so one extra press is worth it.

**Alternatives rejected.**
1. *A pop-up dialog to confirm.* Dialogs move focus to a new window, the default button may be "OK" so a double press still stops the app, and they feel alarming. Confirming on the same button keeps focus where it is.
2. *Two panes (list on the left, details on the right).* Needs left/right navigation and a second focus area that beginners get lost in, and halves the space for big text. One vertical list with one button per row is easier with arrows only.
