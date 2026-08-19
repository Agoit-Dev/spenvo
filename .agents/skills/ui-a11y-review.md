# Skill: ui-a11y-review

**Activate:** when building new screens (especially forms and List-Detail).

## Steps
1. `contentDescription` on every informative image/icon (from strings).
2. Color contrast: use theme colors; verify contrast for text.
3. Touch targets ≥ 48dp for interactive elements (icons, buttons).
4. Scalable fonts: do not fix sp in layouts; test with large font scale.
5. TalkBack: reading order follows the screen's logical order.
6. Forms: visible labels + accessible errors; do not rely on color alone.
7. List-Detail: in a narrow window the detail navigates as a screen (back works);
   in a wide window, both panes accessible.

## Output
- Per-screen checklist + action for each accessibility failure found.