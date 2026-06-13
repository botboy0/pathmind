  UAT Goals — Phase 01 (from 01-UAT.md, 5 tests pending)

  These are the in-game checks the verifier couldn't do headlessly. All on MC 1.21.4 with the profile you just got:

  1. Addon category visible in sidebar
  Open the Pathmind editor → a Scripting category (addon-declared) appears as a tab/section in the sidebar. This proves the CR-01 fix renders.

  -script category is added in the sidebar, but vertical overflow causes it to be partly inaccessible. Will need to add a scrollbar

  2. Script node drag-to-canvas
  Drag the Script node entry from the Scripting category onto the graph canvas → an ADDON node is placed. Proves hover/hit-test → createNodeFromSidebar wiring.
  
  -script category has the correct title and the lua script node but is called addon node when visible in the visual editor. its content "-- Lua script print("Hello from ...")" only appears after reopening the visual editor, before reopening its content is empty

  -script content renders on the wrong z layer as it is seen rendered on top of the slideover drawer
  

  3. Preset round-trip
  Place a Script node (set some script text), save the preset, close and reopen the editor, reload the preset → the node is still there with its script text intact, and survives a re-save.
  Proves CR-02 — previously the node was silently deleted on the next save.

  the addon node survises the roundtrip correctly i tested it by restarting the game and is immediatly visible, indicating to me that the above issue means that there is a smaller problem with it not auto loading the content on creating the node



  4. Execution pass-through
  Run a graph containing the Script node → it activates and completes gracefully (no-op executor), with no "null addonTypeId" warning in the log. Proves CR-03 — the executor is reachable
  through the execution snapshot.

  -i tested it by adding the placeholder script node and attaching a normal workflow after and it did pass through without any error as expected.

  5. Standalone mode
  Remove pathmind-lua-0.1.0.jar from the mods folder (or ask me to redeploy in standalone mode) → Pathmind loads cleanly and the editor works normally with no addon present. Proves the addon
  dependency direction is correct.
  
  -the game starts normal without the addon present and it shows the addon node as a normal blank addon node as expected (i think to make this behavior clearer to users we should add metadata or a visual indicator of which plugin it is missing and that it behaves as a no-op without it)