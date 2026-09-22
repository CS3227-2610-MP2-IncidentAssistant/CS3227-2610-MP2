# Incident Desk visual guide

This is the JavaFX design-system handoff for Incident Desk. Keep one Calm Blue Operations system across Administrator, Reporter, and Responder; role identity comes from navigation and content, not a separate palette. The shared JavaFX stylesheet at `src/main/resources/com/company/incidentdesk/ui/shared/theme/application.css` is the implementation source of truth.

## Design tokens

| Token | Hex | Use |
| --- | --- | --- |
| Navy | `#17233F` | Sidebar, login art, avatar text |
| Action blue | `#4169D8` | Primary actions, active navigation, charts, timeline markers |
| Canvas | `#F5F7FB` | Application content background |
| Surface | `#FFFFFF` | Cards, panels, forms, title bar, dialogs |
| Primary text | `#18243A` | Headings, body text, controls |
| Muted text | `#6E788A` | Supporting text, labels, table headers |
| Border | `#DCE2EC` | Cards, panels, separators |

| Semantic tone | Foreground | Soft background | Use |
| --- | --- | --- | --- |
| Info | `#2854B6` | `#E4ECFF` | Default status |
| Success | `#1F7A55` | `#DDF4E9` | Resolved, healthy, positive metric |
| Warning | `#946016` | `#FFF0D3` | At risk, medium priority |
| Danger | `#B33A3A` | `#FCE4E4` | Error, destructive action, high priority |
| Neutral | `#596579` | `#E9EDF3` | Inactive or unavailable state |

Supporting values: focus outline `#8EAAFF`, hover surface `#F9FAFC`, input border `#C9D1DD`, danger border `#E5B1B1`, sidebar text `#D8E1F6`, progress track `#E8ECF3`, and dialog scrim `rgba(16, 24, 39, .56)`.

## Typography

Use Segoe UI on Windows and select a system fallback in Java when unavailable. Page titles use 25–34px bold text; dialog titles 19px bold; panel headings 17px bold; metric values 29px bold; body and controls 14–16px; labels and table headers 12–13px semibold. Keep headings left aligned. Never rely on color alone.

## Layout and spacing

- Use an 8px rhythm where practical, with modest 8–12px control radii and 12px panel radii.
- Use a 232px full sidebar, collapsing to 78px below 900px and 64px below 600px.
- Content padding is 34px horizontally on wide screens, 24px at medium widths, and 14px on narrow screens.
- Metrics use four, two, then one column as width decreases.
- Forms and details use two columns on wide screens and one column below 600px.
- Prefer `ScrollPane`, `HBox`, `VBox`, `GridPane`, and `Priority.ALWAYS` sizing to horizontal overflow.
- Long text wraps. Controls and actions wrap rather than clip.

## Component rules

### Buttons and navigation

Buttons are at least 40px high with a 9px radius and semibold text. Primary actions use action blue and white text; destructive actions use danger text and a soft danger background; ghost actions are reserved for low emphasis. Every interactive control must have visible hover, focused, pressed, and disabled states. Active navigation uses both position/text and color.

### Fields and validation

Fields use explicit semibold labels, a white surface, 40px minimum height, 8px radius, and input border. Invalid fields use a danger border plus adjacent textual help. Preserve the label and message in the accessibility tree. Text areas start around 108px high and wrap content.

### Cards, tables, and lists

Cards and panels use a white surface, 1px border, and 12px radius. Table headers use muted 12px semibold text on `#F8FAFD`; cells use clear padding and row separators. Interactive rows show a hover surface. Empty states provide a clear next step.

### Badges and progress

Badges use text plus a semantic foreground/background pair, 4px by 9px padding, pill radius, and 12px bold text. Priority includes its written level; a dot may supplement but never replace it. Progress bars use an 8px blue fill on a neutral track and expose a textual/accessible value.

### Attachments, comments, and timeline

Attachment tiles are at least 92px high with a dashed border and visible file type. Comments use a 34px avatar and a neutral message bubble. Timeline markers use blue dots and textual event descriptions with locally formatted time.

### Dialogs and notifications

Confirmation dialogs state the consequence, have explicit cancel/confirm actions, support Escape, receive focus, and return focus to the trigger. Success notifications use text and an accessible role, not color alone. Page-level errors include a concise explanation and recovery action.

## Accessibility

- Use semantic JavaFX controls, explicit labels, meaningful action names, and table headers.
- Set accessible text when the visual label does not fully describe a control.
- Preserve logical keyboard order and visible focus.
- Communicate status with text or icons as well as color.
- Keep text/background pairs readable and support wrapped content at narrow widths.
- Skip optional animation when reduced motion is requested.

## JavaFX mapping

| Product pattern | JavaFX control/layout |
| --- | --- |
| Sidebar/navigation | `VBox`, `Button`, `ScrollPane`, pseudo-classes |
| Panels and metrics | `VBox`, `HBox`, `GridPane`, `BorderPane`, `Region` |
| Tables and lists | `TableView`, `ListView` |
| Forms | `Label`, `TextField`, `ComboBox`, `DatePicker`, `TextArea`, `CheckBox` |
| Attachments | `FileChooser`, `ImageView`, `MediaView`, `TilePane`/`FlowPane` |
| Comments and timeline | `VBox`, `HBox`, `ScrollPane`, `Separator` |
| Confirmation | `Dialog` or `DialogPane` |
| Notification overlay | `StackPane` with an `HBox`/`Label` |
| Charts and progress | `BarChart`, `LineChart`, `ProgressBar` |

## JavaFX CSS boundaries

JavaFX CSS does not support browser custom properties, Grid/Flexbox, media queries, fixed positioning, `z-index`, browser overflow, transitions, or arbitrary font weights. Keep responsive behavior and layout in Java; use looked-up colors or the small repeated token set in CSS. Use `-fx-background-radius`, `-fx-border-radius`, `-fx-border-color`, and `-fx-effect`. Custom window behavior belongs in Java, and rounded backgrounds may require explicit child radii or clipping.
