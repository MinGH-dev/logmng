#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Emit req-20260323 approver-eligibility wireframe SVGs as UTF-8 (ASCII-only source)."""
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "assets" / "svg" / "scenes"
# Wire-annotation body: single left margin (matches white card inner edge x=288).
WIRE_ANNO_TEXT_X = 288
WIRE_ANNO_TEXT_X_SVG4 = 40


def u(*parts: str) -> str:
    return "".join(parts)


# Korean / mixed labels (unicode escapes keep this file ASCII-safe on all editors)
L = {
    "admin": u("\uad00\ub9ac"),
    "user_mgmt": u("\uc0ac\uc6a9\uc790 \uad00\ub9ac"),
    "perm_groups": u("\uad8c\ud55c \uadf8\ub8f9 \uad00\ub9ac"),
    "app_title": u("\ub85c\uadf8 \uad00\ub9ac \uc2dc\uc2a4\ud15c"),
    "user_final": u("\uc0ac\uc6a9\uc790 \uad00\ub9ac (\ucd5c\uc885 \ubaa9\ud45c)"),
    "hint_um": u(
        "\ubcf5\ud638\ud654 \uc2b9\uc778 \uc790\uaca9\uc740 \uc0ac\uc6a9\uc790 \ubaa9\ub85d\uc5d0 \ubcc4\ub3c4 \uceec\ub7fc\uc73c\ub85c \ub450\uc9c0 \uc54a\uc74c. "
        "\ud310\ub2e8\uc740 \uad8c\ud55c \uadf8\ub8f9 \ud654\uba74 \uc2b9\uc778\u00b7API effective \uc2b9\uc778\ub9cc \uc0ac\uc6a9."
    ),
    "ops_dept": u("[OPS] \uc6b4\uc601\ud300"),
    "col_name": u("\uc0ac\uc6a9\uc790\uba85"),
    "col_uid": u("\uc0ac\uc6a9\uc790 ID"),
    "col_grade": u("\uc9c1\uae09"),
    "col_title": u("\uc9c1\ucc45"),
    "col_group": u("\uad8c\ud55c \uadf8\ub8f9"),
    "hong": u("\ud64d\uae38\ub3d9"),
    "kim": u("\uae40\ucca0\uc218"),
    "team_lead": u("\ud300\uc7a5"),
    "lead": u("\ub9ac\ub4dc"),
    "deputy": u("\ub300\ub9ac"),
    "engineer": u("\uc5d4\uc9c0\ub2c8\uc5b4"),
    "grp_ops": u("\uc6b4\uc601 \uadf8\ub8f9"),
    "grp_dev": u("\uac1c\ubc1c \uadf8\ub8f9"),
    "rm_col": u("\uc81c\uac70: \uc2b9\uc778\uc790 \uc5f4"),
    "rm_flag": u("\uc81c\uac70: isApprover \ud45c\uc2dc"),
    "dev_note": u("\uac1c\ubc1c\u00b7\ud14c\uc2a4\ud2b8 \uba54\ubaa8 (GET /api/users \ub4f1)"),
    "dev_b1": u(
        "\uc751\ub2f5\uc5d0\uc11c isApprover \ud544\ub4dc \uc5c6\uc74c. TC-13 \ud68c\uadc0: "
        "\uadf8\ub9ac\ub4dc \ud5e4\ub354\uc5d0 \uc2b9\uc778\uc790 \uceec\ub7fc\uc774 \uc5c6\uc5b4\uc57c \ud568."
    ),
    "dev_b2": u(
        "\uc2b9\uc778 \uac00\ub2a5 \uc5ec\ubd80: \uad8c\ud55c \uadf8\ub8f9 \uad00\ub9ac(\uac80\uc0c9 \uc774\ub825\u00b7\uc2b9\uc778 \ub300\uae30 \uccb4\ud06c) "
        "\ubc0f GET /api/auth/me\uc758 screenFunctions\ub85c \ud655\uc778."
    ),
    "dev_b3": u(
        "P2-2: \uc2b9\uc778 \uc561\uc158\uc740 \uc694\uccad\uc790\uc640 \ub3d9\uc77c department_code\uc77c \ub54c\ub9cc "
        "\ud5c8\uc6a9 (\ubcc4\ub3c4 \ud654\uba74 \uaddc\uce59)."
    ),
    "pg_title": u(
        "\uad8c\ud55c \uadf8\ub8f9 \uad00\ub9ac \u2014 \ud654\uba74\ubcc4 \uae30\ub2a5 (\uc811\uadfc\u00b7\ubc94\uc704\u00b7\uc218\uc815\u00b7\uc2b9\uc778\u00b7\ubcf5\ud638\ud654)"
    ),
    "pg_sub": u(
        "SoT: \uad8c\ud55c \uadf8\ub8f9 \ub370\uc774\ud130(decrypt_approver \uc5c6\uc74c). "
        "\uc544\ub798 \ud45c\ub294 ScreenSelectionTree + specs \xa7 1.1.1 \uae30\uc900 \ud654\uba74\u00b7\uae30\ub2a5(\uad50\uc721\uc6a9 \ub9e4\ud2b8\ub9ad\uc2a4). "
        "\uccab \uc5f4 \u300c\uba54\ub274 \uad6c\ubd84\u300d = \uc0ac\uc774\ub4dc\ubc14 \uba54\ub274; \uc67c \ubaa9\ub85d = \ud3b8\uc9d1 \uc911 \uadf8\ub8f9. "
        "\ud2b8\ub9ac\uc5d0\uc11c \ud654\uba74 \uccb4\ud06c \u2192 \uadf8 \uadf8\ub8f9 \uc0ac\uc6a9\uc790\uac00 \ud574\ub2f9 \uba54\ub274\u00b7API \uc0ac\uc6a9. \uccb4\ud06c \ud574\uc81c \u2192 \ud589\u00b7\uba54\ub274\uc5d0\uc11c \uc81c\uc678. "
        "\uc5f4 \u300c\uba54\ub274\u00b7API \uc0ac\uc6a9\u300d = \uc0ac\uc6a9 \uc5ec\ubd80(API read\ub294 \ubcc4\ub3c4 \uc5f4 \uc5c6\uc74c)."
    ),
    "list_title": u("\uad8c\ud55c \uadf8\ub8f9 \ubaa9\ub85d"),
    "list_btn_add": u("\ucd94\uac00"),
    "list_btn_del": u("\uc0ad\uc81c"),
    "detail_title": u(
        "\uc120\ud0dd\ud55c \uad8c\ud55c \uadf8\ub8f9: \uba54\ub274\u00b7API \uc0ac\uc6a9 \uc5ec\ubd80 \ubc0f \uc138\ubd80 \uae30\ub2a5 (\uc2e4\uc81c UI\ub294 \uc67c\ucabd \uba54\ub274 \uadf8\ub8f9 \ub2e8\uc704 \ud2b8\ub9ac)"
    ),
    "hdr_cat": u("\uba54\ub274 \uad6c\ubd84"),
    "hdr_screen": u("\ud654\uba74\uba85"),
    "hdr_access_1": u("\uba54\ub274\u00b7API"),
    "hdr_access_2": u("\uc0ac\uc6a9"),
    "hdr_scope": u("\uc870\ud68c \ubc94\uc704"),
    "hdr_write": u("\uc218\uc815"),
    "hdr_appr": u("\uc2b9\uc778 \uad8c\ud55c"),
    "hdr_decrypt": u("\ubcf5\ud638\ud654 \ud5c8\uc6a9"),
    "cell_na": u("\u2014"),
    "cell_scope_dd": u("\ub4dc\ub86d\ub2e4\uc6b4\n\ubcf8\uc778\u00b7\ubd80\uc11c\u00b7\uc804\uccb4"),
    "cell_scope_dd_appr": u("\ub4dc\ub86d\ub2e4\uc6b4\n(\uc2b9\uc778 \uc2dc \ubd80\uc11c \uace0\uc815)"),
    # Scope column guidance: visible wire legend in matrix SVG (not XML comments)
    "scope_wire_legend_title": u("\u25a0 \uc870\ud68c \ubc94\uc704 \u00b7 \uc640\uc774\uc5b4 \uc8fc\uc11d (\uc81c\ud488 UI \uc544\ub2d8)"),
    "scope_wire_legend_row_pointer": u(
        "\u203b \ud589\ubcc4 screenId\u00b7\uae30\ub2a5 \uc694\uc57d\uc740 \uc704\ucabd \uad6c\ubd84\uc120 \uc774\uc804 "
        "\ube14\ub85d #screen-row-wire-legend-req02\ub97c \ucc38\uace0."
    ),
    "scope_wire_legend_col": u(
        "\uc5f4 \uc694\uc57d: self|team|all \uc800\uc7a5 \ub300\uc0c1\uc740 activity-log, statistics, search-history, pending-approvals "
        "\ub124 \ud654\uba74\ubfd0. \uadf8 \uc678 \ud589\uc740 \u2014."
    ),
    "scope_wire_legend_pb": u(
        "pb-feplog: \ube44\ub300\uc0c1. \ub85c\uadf8 \uac80\uc0c9(main)\uc740 \uc0ac\uc6a9\uc790/\uc694\uccad\uc790 \ub9e5\ub77d \uc2a4\ucf54\ud504 \uc544\ub2d8; \uad8c\ud55c\uadf8\ub8f9\uc5d0 self|team|all \uc5c6\uc74c."
    ),
    "scope_wire_legend_java": u("java-fw-imagelog: \ub3d9\uc77c \ube44\ub300\uc0c1. \uc870\ud68c \ubc94\uc704 \uc5f4 \u2014."),
    "scope_wire_legend_act": u(
        "activity-log: \uc0ac\uc6a9\uc790 \ub9e5\ub77d. self|team|all. self\uc77c \ub54c \ubd80\uc11c\u00b7\uc774\ub984\u00b7ID \ud604\uc7ac \uc0ac\uc6a9\uc790 \uace0\uc815(\uc77d\uae30\uc804\uc6a9). "
        "team: \ub3d9\uc77c \ubd80\uc11c \ubc94\uc704; \ubd80\uc11c \uc635\uc158 activity-log/statistics/search-history \uacf5\uc720 API. SoT search-fields-by-screen \xa72.1."
    ),
    "scope_wire_legend_sh": u(
        "search-history: \uc694\uccad\uc790 \ub9e5\ub77d. self|team|all. self\uc77c \ub54c \uc694\uccad\uc790 \ube14\ub85d \uc228\uae40 \ubc0f API \uc694\uccad\uc790 \ud30c\ub77c\ubbf8\ud130 \ube44\uc6c0. SoT \xa74.1."
    ),
    "scope_wire_legend_pend": u(
        "pending-approvals: search-history\uc640 \ub3d9\uc77c \uc2a4\ucf54\ud504\ub85c \ub300\uae30 \ubaa9\ub85d \ud544\ud130. "
        "\uc640\uc774\uc5b4: \uc2b9\uc778 \uccb4\ud06c \ud589\uc740 \uc870\ud68c \ubc94\uc704 \ub4dc\ub86d\ub2e4\uc6b4 \ubd80\uc11c \uace0\uc815(\uc7a0\uae40) \uc2a4\ud0c0\uc77c."
    ),
    "scope_wire_legend_stat": u(
        "statistics: activity-log\uacfc \ub3d9\uc77c \uacc4\uc5f4(UserContextFilterBlock). self|team|all. SoT \xa73.1."
    ),
    "scope_wire_legend_um_pgm": u(
        "user-management / permission-group-management: \uc870\ud68c \ubc94\uc704 \ube44\ub300\uc0c1. \uad00\ub9ac CRUD. \uc5f4 \u2014."
    ),
    "scope_wire_legend_dept": u("department-approvers: \uc870\ud68c \ubc94\uc704 \ube44\ub300\uc0c1(\ud604\uc7ac \ub9e4\ud2b8\ub9ad\uc2a4). \uc5f4 \u2014."),
    "scope_wire_legend_primitive": u(
        "\uc870\ud68c \ubc94\uc704 \uc5f4: \ucef4\ud329\ud2b8 \uc140(\ud55c\uae00 2\uc790 + \u25bc). "
        "root data-grid-select-primitive\ub294 \uc2a4\ud0c0\uc77c \ucc38\uc870\uc6a9; \uc5f4 \ud3ed\uc740 \ud14c\uc774\ube14 \uc140\ubcf4\ub2e4 \uc881\uc74c. "
        "\uc2b9\uc778 \ud589: \uc7a0\uae40 \ud544\ub4dc(\uc640\uc774\uc5b4 \uaddc\uce59)."
    ),
    # Wireframe: approve is approve=true|false (checkbox), not 조회/승인 mutual radio.
    "lbl_pb": u("PB FEP Log"),
    "lbl_java": u("Java FW Image Log"),
    "lbl_act": u("\ud65c\ub3d9 \uc774\ub825"),
    "lbl_stat": u("\ud65c\ub3d9\ub85c\uadf8 \ud1b5\uacc4"),
    "lbl_dept_appr": u("\ubd80\uc11c \uc2b9\uc778\uc790 \uad00\ub9ac"),
    "grp_log": u("\ub85c\uadf8 \uac80\uc0c9"),
    "grp_hist": u("\uc774\ub825\u00b7\uc2b9\uc778"),
    "grp_stat": u("\ud1b5\uacc4"),
    "pg_matrix_layout_note": u(
        "\ub808\uc774\uc544\uc6c3: \uc67c \ud328\ub110 = \uad8c\ud55c \uadf8\ub8f9 \ubaa9\ub85d(\uce74\ub4dc \ub0b4 \uc0ac\uc774\ub4dc\ubc14), "
        "\ubaa9\ub85d \uc0c1\ub2e8 \ucd94\uac00\u00b7\uc0ad\uc81c. "
        "\uc624\ub978\ucabd = \ud654\uba74\u00b7\uae30\ub2a5 \ub9e4\ud2b8\ub9ad\uc2a4. \ub9e4\ud2b8\ub9ad\uc2a4\ub294 \ud398\uc774\uc9c0\ub124\uc774\uc158 \uc5c6\uc774 "
        "\uc804\uccb4 \ud589\uc744 \ud328\ub110\uc5d0 \ud45c\uc2dc(\ud589 \ub9ce\uc73c\uba74 \ud328\ub110 \ub0b4 \uc2a4\ud06c\ub864). "
        "\uc870\ud68c \ubc94\uc704 \uc5f4\uc740 \ud55c\uae00 2\uc790 \ub4dc\ub86d\ub2e4\uc6b4 \ucef4\ud329\ud2b8 \uc140. "
        "\uc77c\ubc18 \ubaa9\ub85d \uadf8\ub9ac\ub4dc(grid-and-table.md)\uc640 \uad6c\ubd84."
    ),
    "pg_foot_ui": u(
        "UI \uad6c\ud604: PermissionGroupPanel \u00b7 ScreenSelectionTree.js "
        "(\ud2b8\ub9ac \uccb4\ud06c + \ud45c\ub294 14px \uccb4\ud06c\ubc15\uc2a4 \uc77c\uad00; \uc774 \ud45c\ub294 \uac1c\ub150 \uc815\ub9ac\uc6a9)"
    ),
    "pg_foot_spec": u(
        "\uc2a4\ud399 \uae30\ub2a5: screenFunctionDescriptions.js "
        "(SCREENS_WITH_WRITE, APPROVE, DECRYPT) \u00b7 permission-group-hierarchy.spec.yaml \xa7 1.1.1"
    ),
    "pg_foot_extra": u(
        "department-approvers, user-permission-hierarchy: \uc2a4\ud399\uc0c1 \uc870\ud68c+\uc218\uc815 \uc9c0\uc6d0. "
        "\ud604\uc7ac MENU_TREE \uc120\ud0dd\uae30\uc5d0\ub294 \ubbf8\ud3ec\ud568 (\ubc31\uc5d4\ub4dc allowedScreens\uc5d0\ub9cc)."
    ),
    "pg_foot_fe": u(
        "Frontend \uc5d0\uc774\uc804\ud2b8: \ud654\uba74\uba85 \uc5f4\uc740 \uc0ac\uc6a9\uc790 \ub178\ucd9c \ub77c\ubca8\ub9cc. "
        "screenId\u00b7\ud589\ubcc4 \uae30\ub2a5 \uc694\uc57d\uc740 #screen-row-wire-legend-req02\uc5d0\ub9cc \ub450\uace0 \ud45c \uc5f4\uc5d0 \ub123\uc9c0 \ub9d0 \uac83. "
        "\uc870\ud68c \ubc94\uc704 \ud654\uba74\ubcc4 \uc758\ubbf8\ub294 #scope-wire-legend-req02."
    ),
    "scope_dd_value": u("\ubd80\uc11c"),
    "ux_banner_t": u(
        "UX \uad8c\uc7a5 (\uc640\uc774\uc5b4\ud504\ub808\uc784 \ubc18\uc601 \u2014 \ud504\ub860\ud2b8 \ucf54\ub4dc \ube44\ub300\uc0c1)"
    ),
    "ux_b1": u(
        "\ub9e5\ub77d: \uc624\ub978\ucabd \uc81c\ubaa9\uc5d0 \uc120\ud0dd\ud55c \uad8c\ud55c \uadf8\ub8f9\uba85\uc744 \uc0c1\uc2dc \ud45c\uc2dc (\ud3b8\uc9d1 \uc911\uc778 \ub300\uc0c1\uc744 \uc783\uc9c0 \uc54a\uae30)."
    ),
    "ux_b2": u(
        "\uc810\uc9c4\uc801 \uacf5\uac1c: \uba54\ub274 \uadf8\ub8f9 \ub2e8\uc704 \uc811\uae30 + \ud55c \uc904 \uc694\uc57d \u2192 \uc0c1\uc138 \ucf58\ud2b8\ub864 (\ud45c\ub294 \uc694\uc57d \uc608\uc2dc)."
    ),
    "ux_b3": u(
        "\ud53c\ub4dc\ubc31: \uc800\uc7a5 \uc131\uacf5 \uc2a4\ub0b5\ubc14/\ud1a0\uc2a4\ud2b8, \uc2e4\ud328 \uc2dc \ud654\uba74\u00b7\ud544\ub4dc \ub2e8\uc704 \uba54\uc2dc\uc9c0."
    ),
    "ux_b4": u(
        "\uc811\uadfc\uc131: \uc774\uc9c4 \uae30\ub2a5\uc740 \ubaa8\ub450 14px \uccb4\ud06c\ubc15\uc2a4\ub85c \ud1b5\uc77c(\uccb4\ud06c \uc5ec\ubd80 + \uc0c9\u00b7\uc2a4\ud2b8\ub85c \uad6c\ubd84)."
    ),
    "ux_ctx": u(
        "\uc9c0\uae08 \ud3b8\uc9d1 \uc911: \uc6b4\uc601 \uadf8\ub8f9 \u2014 \uc544\ub798\ub294 \uc774 \uad8c\ud55c \uadf8\ub8f9\uc758 \ud654\uba74 \uad8c\ud55c\uc785\ub2c8\ub2e4."
    ),
    "ux_sum_pb": u("\uc694\uc57d: \uc811\uadfc + \ubcf5\ud638\ud654(\uc635\uc158)"),
    "ux_sum_sh": u("\uc694\uc57d: \ubaa9\ub85d\ubc94\uc704 + \uc2b9\uc778"),
    "ux_sum_um": u("\uc694\uc57d: \uc811\uadfc + \uc218\uc815(\uc635\uc158)"),
    "ux_sum_act": u("\uc694\uc57d: \uc811\uadfc + \ubaa9\ub85d\ubc94\uc704"),
    "ux_sum_excl": u("\uc608\uc2dc: \uc774 \uadf8\ub8f9\uc5d0 \uc5c6\ub294 \ud654\uba74 \u2192 \uba54\ub274\u00b7API \ubbf8\uc0ac\uc6a9, \ud589 \uc5c6\uc74c"),
    "ux_snack": u("\uc800\uc7a5\ub418\uc5c8\uc2b5\ub2c8\ub2e4"),
    "ux_snack_sub": u("(\ubcc0\uacbd \uc694\uc57d \ub610\ub294 \ub514\ud37c \ubbf8\ub9ac\ubcf4\uae30)"),
    "note_t": u("\ubc31\uc5d4\ub4dc \ud310\ubcc4 \uc21c\uc11c (\ud14c\uc2a4\ud2b8 \uc2dc \uae30\ub300\uac12)"),
    "n1": u("1) app_user_permission_group \u2192 permission_group_screen \uc5d0\uc11c \uc704 \ud654\uba74\ub4e4\uc758 approve=true \uc5ec\ubd80"),
    "n2": u(
        "2) app_user.is_system_admin=true \uc774\uba74 effective \uc2b9\uc778 false \ub85c \uc0c1\uc1c4 "
        "(\uadf8\ub8f9\uc5d0 \uc2b9\uc778\uc774 \uc788\uc5b4\ub3c4 \ubcf5\ud638\ud654 \uc2b9\uc778 \ubd88\uac00)"
    ),
    "n3": u("3) is_system_admin=false \uc778 ADMIN_EXT \ub4f1\uc740 \uadf8\ub8f9 \uacb0\uacfc\ub9cc \uc801\uc6a9"),
    "n4": u("4) canApproveForRequester: effective \uc2b9\uc778 AND \uc694\uccad\uc790\uc640 \ub3d9\uc77c department_code (P2-2)"),
    "n5": u("5) \ucee4\ud2b8\uc624\ubc84 (A): decrypt_approver \ub294 \uc790\uaca9 \ud310\ub2e8\uc5d0 \uc0ac\uc6a9\ud558\uc9c0 \uc54a\uc74c"),
    "n6": u("TC \ucc38\uc870: TC-01~TC-07c, TC-10, TC-11"),
    "list_mui_note": u("MUI DataGrid: \ud45c\uc5d0\uc11c \uceec\ub7fc \uac80\uc0c9\u00b7\ub9ac\uc0ac\uc774\uc988 \uac00\ub2a5"),
    "save": u("\uc800\uc7a5"),
    "hist_nav": u("\uc774\ub825\u00b7\uc2b9\uc778"),
    "nav_act": u("\ud65c\ub3d9 \uc774\ub825"),
    "nav_sh": u("\uac80\uc0c9 \uc774\ub825"),
    "nav_pend": u("\ubcf5\ud638\ud654 \uc2b9\uc778 \uad00\ub9ac"),
    "fe_warn_title": u("\ud504\ub860\ud2b8\uc5d4\ub4dc \uc8fc\uc758 (\ub77c\uc6b0\ud305 \u00b7 \uc5d0\uc774\uc804\ud2b8 \ud611\ub780 \ubc29\uc9c0)"),
    "fe_warn_l1": u(
        "\uc0ac\uc774\ub4dc\ubc14\uc5d0 \u300c\uc0ac\uc6a9\uc790 \uad8c\ud55c \uacc4\uce35\u300d \uba54\ub274 \uc5c6\uc74c. "
        "\uad00\ub9ac \u2192 \uc0ac\uc6a9\uc790 \uad00\ub9ac\uac00 \uc720\uc77c \uc9c4\uc785."
    ),
    "fe_warn_l2": u(
        "App.js: currentView \uc774 user-management \uc774\ub4e0 user-permission-hierarchy \uc774\ub4e0 "
        "\ub3d9\uc77c\ud558\uac8c UserManagement \ucef4\ud3ec\ub10c\ud2b8\ub9cc \ub80c\ub354."
    ),
    "fe_warn_l3": u(
        "UserPermissionHierarchy.js \ub294 \uc800\uc7a5\uc18c\uc5d0 \uc788\uc73c\ub098 App\uc5d0\uc11c import \ub418\uc9c0 "
        "\uc54a\uc74c. \ubcc4\ub3c4 \ud654\uba74\uc744 \uc0c8\ub85c \ub9cc\ub4e4\uc9c0 \ub9d0 \uac83."
    ),
    "uh_title": u("TC-09 \u00b7 user-permission-hierarchy (\uacc4\uc57d \ud654\uba74 ID) / \uc2e4\uc81c UI = \uc0ac\uc6a9\uc790 \uad00\ub9ac"),
    "uh_hint": u(
        "\ubd80\uc11c \ud2b8\ub9ac: GET .../departments/user-permission-hierarchy?format=tree (\uc774 \ud654\uba74\uc5d0\uc11c \ud638\ucd9c). "
        "\uacc4\uce35 API \uc751\ub2f5\u00b7\uc0ac\uc6a9\uc790 \ubaa9\ub85d\uc5d0\uc11c isApprover \ub4f1 \uc81c\uac70 \ub300\uc0c1 \ud544\ub4dc \uc5c6\uc74c."
    ),
    "uh_impl": u(
        "\uad6c\ud604 \ucc38\uc870: frontend/src/components/UserManagement/UserManagement.js "
        "\u00b7 services/permissionGroupService.getUserPermissionHierarchy"
    ),
    "uh_layout_note": u(
        "\uc544\ub798 \ub808\uc774\uc544\uc6c3\uc740 req-20260323-01\uacfc \ub3d9\uc77c \ub2e8\uc77c \ud328\ub110(\ubd80\uc11c \ud2b8\ub9ac + \uadf8\ub8f9 \uc5f4)\uc774\ub2e4. "
        "\uc624\ub978\ucabd \ubcc4\ub3c4 \uc0c1\uc138 \ud328\ub110\uc740 \uc5c6\uc74c."
    ),
    "tree_title": u("\ubd80\uc11c \ud2b8\ub9ac (\ud3b8\uce58 \ud6c4 \uc0ac\uc6a9\uc790 \ud589)"),
    "rm_box_t": u("\uc81c\uac70 \ub300\uc0c1 (\uc751\ub2f5 \ud544\ub4dc\u00b7UI)"),
    "rm_box_b": u("isApprover, \uc2b9\uc778\uc790 \uc5ec\ubd80 \ub2e8\ub3c5 \ud45c\uc2dc, decrypt_approver \uc5f0\ub3d9 \ubb38\uad6c"),
    "tc9": u("TC-09: \uacc4\uce35 \uc751\ub2f5\u00b7\ubaa9\ub85d\uc5d0 decrypt_approver \uc804\uc6a9 \ud50c\ub798\uadf8 \uc5c6\uc74c"),
    # Test states board
    "ts_main": u("\ubcf5\ud638\ud654 \uc2b9\uc778 \ud750\ub984 \u2014 UI\u00b7API \uae30\ub300 \uc0c1\ud0dc (\uac1c\ubc1c\u00b7\ud14c\uc2a4\ud2b8\uc6a9)"),
    "ts_sub": u(
        "\ubb38\uc11c: 20260323-approver-eligibility \uc694\uad6c\uc11c \xa7 3 TC. "
        "\uce74\ub4dc \ub0b4 \ubb38\uc790\ub294 API\u00b7\ubcf5\ud638\ud654 \uc2b9\uc778 \ud14c\uc2a4\ud2b8 \uc0c1\ud0dc \uc124\uba85\uc6a9(\uc0d8\ud50c); "
        "i18n\u00b7\uacc4\uc57d SoT \uc544\ub2d8."
    ),
    "a_t": u("A) \uc77c\ubc18 \uc2b9\uc778\uc790 \u00b7 \ub3d9\uc77c \ubd80\uc11c (TC-05, TC-11)"),
    "a_b1": u("\uc870\uac74: is_system_admin=false, \uadf8\ub8f9\uc5d0 pending-approvals \uc2b9\uc778 O, \uc694\uccad\uc790 dept = \uc2b9\uc778\uc790 dept"),
    "a_ok": u("\uae30\ub300: \ubaa9\ub85d\u00b7\uc0c1\uc138\uc5d0\uc11c \uc2b9\uc778 \ub300\uae30 \ud589 \ud45c\uc2dc, \uc2b9\uc778\u00b7\ubc18\ub824 \ubc84\ud2bc \ud65c\uc131"),
    "a_code": u("canApproveForRequester = true"),
    "a_b2": u("\uac80\uc0c9 \uc774\ub825 \ud654\uba74\uc5d0\ub3c4 \ub3d9\uc77c \uc815\ucc45\uc5d0 \ub9de\ub294 \uc561\uc158 \ub178\ucd9c"),
    "a_man": u("\uc218\ub3d9: \ube0c\ub77c\uc6b0\uc800 TC-11, \uc815\ubcf4 \ub178\ucd9c Q5"),
    "b_t": u("B) \uc2b9\uc778 \uadf8\ub8f9 O \uc774\uc9c0\ub9cc \ud0c0 \ubd80\uc11c \uc694\uccad (TC-06 P2-2)"),
    "b_b1": u("\uc870\uac74: effective \uc2b9\uc778 true \uc774\uc9c0\ub9cc requester.department_code \u2260 approver.department_code"),
    "b_dn": u("\uae30\ub300: \uc2b9\uc778 API 403, \uacc4\uc57d\uc5d0 \ub530\ub978 FUNCTION_NOT_ALLOWED"),
    "b_b2": u("UI: \ud574\ub2f9 \ud589 \ubbf8\ub178\ucd9c \ub610\ub294 \ubc84\ud2bc \ube44\ud65c\uc131\u00b7\uac70\ubd80 (\uc81c\ud488 UX\uc5d0 \ub9de\uac8c)"),
    "b_code": u("NOT_APPROVER \uc640 \uad6c\ubd84: TC-06b \ub294 \uc790\uaca9 \uc5c6\uc74c"),
    "c_t": u("C) \uc2b9\uc778 \uadf8\ub8f9 \uc5c6\uc74c \u00b7 \ube44\uc2b9\uc778\uc790 (TC-06b, TC-03)"),
    "c_b1": u("\uc870\uac74: search-history / pending-approvals \uc5d0 approve=false \ub610\ub294 \ubbf8\ubc30\uc815"),
    "c_dn": u("\uae30\ub300: NOT_APPROVER \ub610\ub294 FORBIDDEN_NOT_APPROVER (\uacc4\uc57d \uba85\uce6d \uc900\uc218)"),
    "c_b2": u("UI: \uc2b9\uc778 \ub300\uae30 \uba54\ub274\ub294 screen access \uc5d0 \ub530\ub78c, \uc561\uc158\uc740 \ubd88\uac00"),
    "c_code": u("TC-15 IDOR: \ud0c0 \uc0ac\uc6a9\uc790 \ub300\uae30 \uac74 \uc870\ud68c\u00b7\uc2b9\uc778 \ubd88\uac00"),
    "d_t": u("D) \uc2dc\uc2a4\ud15c \uad00\ub9ac\uc790 \u00b7 \uadf8\ub8f9 \uc2b9\uc778 \uc788\uc74c (TC-07b)"),
    "d_b1": u("\uc870\uac74: is_system_admin=true, \uadf8\ub8f9 \ub9e4\ud2b8\ub9ad\uc2a4\uc5d0 \uc2b9\uc778 \uccb4\ud06c O, \ub3d9\uc77c \ubd80\uc11c\ub77c\ub3c4"),
    "d_dn": u("\uae30\ub300: effective \uc2b9\uc778 false, canApproveForRequester false, \uc2b9\uc778\u00b7\ubc18\ub824 \uac70\ubd80"),
    "d_b2": u("TC-10: GET /api/auth/me \uc758 \ud574\ub2f9 \ud654\uba74 approve \ud50c\ub798\uadf8 false"),
    "d_code": u("ADMIN_EXT \ub294 is_system_admin=false \uc774\uba74 TC-07c \ucc98\ub7fc \uadf8\ub8f9\ub9cc \ub530\ub984"),
    "e_t": u("E) \ucee4\ud2b8\uc624\ubc84 (A) \ub808\uac70\uc2dc decrypt_approver \ub9cc \uc788\uc74c (TC-16, TC-18, TC-DB-04)"),
    "e_b1": u("\uc870\uac74: \uad8c\ud55c \uadf8\ub8f9\uc5d0 \uc2b9\uc778 \uc5c6\uc74c, DB \uc5d0\ub9cc legacy \ud589 \uc874\uc7ac"),
    "e_dn": u("\uae30\ub300: \uc790\uaca9 false, \uc571\uc740 \uadf8\ub8f9 \ud14c\uc774\ube14\ub9cc \uc74d\uc74c \u2014 \ud589 \ubb34\uc2dc"),
    "e_b2": u("\uc6b4\uc601: Q4 M-3 \uc804 \uc218\ub3d9 \uadf8\ub8f9 \ubd80\uc5ec\ub85c \uc815\ud569"),
    "e_aud": u("\uac10\uc0ac TC-14: \uc2b9\uc778 \uc2dc numeric user id, \uc694\uccad \uc2dd\ubcc4\uc790, \uc2dc\uac01, \uacb0\uacfc \ub85c\uadf8 \uc720\uc9c0"),
    "f_t": u("\ube60\ub978 \uccb4\ud06c\ub9ac\uc2a4\ud2b8"),
    "f_b1": u("N-1 Q6: \uad8c\ud55c \ubcc0\uacbd \ud6c4 \ub2e4\uc74c \uc694\uccad\ubd80\ud130 \ubc18\uc601 \u00b7 \uc9e7\uc740 /auth/me \uce90\uc2dc \uc2dc staleness \ubb38\uc11c\ud654"),
    "f_b2": u("\uc5d0\ub7ec \ub9f5\ud551 \xa7 2: (a) \ube44\uc2b9\uc778\uc790 NOT_APPROVER \uacc4\uc5f4 (b) \uc2a4\ucf54\ud504\u00b7\uae30\ub2a5 FUNCTION_NOT_ALLOWED (c) \ubbf8\uc778\uc99d 401"),
    "f_b3": u("\ud504\ub860\ud2b8: \uc0ac\uc6a9\uc790 \uad00\ub9ac TC-13, \uad8c\ud55c \uadf8\ub8f9 \ubb38\uad6c\uc5d0\uc11c decrypt_approver \uc774\uc911 \uad00\ub9ac \uc548\ub0b4 \uc81c\uac70"),
    # Visible in image viewers (XML comments are not); same policy as root data-* for humans + agents
    "meta_vis_title": u(
        "\u25a0 \uc640\uc774\uc5b4 \uc8fc\uc11d \u2014 \ub8e8\ud2b8 data-* \uc694\uc57d (\uc774\ubbf8\uc9c0 \ubdf0\uc5b4 \ud45c\uc2dc \u00b7 \uc81c\ud488 UI \ubb38\uad6c \uc544\ub2d8)"
    ),
    "meta_vis_l1": u(
        "\ub8e8\ud2b8 <svg> data-wireframe-* \u00b7 data-col-screen-name-policy \u00b7 data-ref = "
        "\ud574\uc11d \uaddc\uce59 (\uc5d0\uc774\uc804\ud2b8\u00b7\uc0ac\ub78c \uacf5\ud1b5)."
    ),
    "meta_vis_l2": u(
        "\uad8c\ud55c\uadf8\ub8f9 \ub9e4\ud2b8\ub9ad\uc2a4: \ud589\ubcc4 screenId\u00b7\uae30\ub2a5 = #screen-row-wire-legend-req02; "
        "\uc870\ud68c \ubc94\uc704 = #scope-wire-legend-req02."
    ),
    "meta_vis_l3": u("\uc804\ubb38 \uaddc\uce59: .cursor/rules/svg-wireframe-semantics.mdc"),
    "meta_vis_l4": u(
        "\uadf8 \uc678 \uc640\uc774\uc5b4(\uc0ac\uc6a9\uc790 \uad00\ub9ac \ub4f1): \uc5f4\ubcc4 \uc8fc\uc11d\uc740 \ub3d9\uc77c\ud558\uac8c \ud558\ub2e8 \uc2ac\ub7a9 \ud14d\uc2a4\ud2b8. "
        "\uc774 \ub8e8\ud2b8 data-* \uc694\uc57d\ub3c4 data-wireframe-chrome\uc73c\ub85c \uc228\uae40."
    ),
    # Visible markers: not shipped UI (same bucket as other wire annotations)
    "wf_anno_tag": u("\uc640\uc774\uc5b4 \uc8fc\uc11d \u00b7 \uc81c\ud488 UI \uc544\ub2d8"),
    "wf_mock_ui_tag": u("UI \ubaa9\uc5c5 (\uc2e4\uc81c \ud654\uba74 \uc608\uc2dc)"),
    "wf_legend_short": u(
        "\u25a0 \uc2e4\uc120\u00b7\uc77c\ubc18 \ud14c\ub450\ub9ac = \ud654\uba74 \ubaa9\uc5c5 / "
        "\uc810\uc120\u00b7\ud68c\uc0c9 \ud0dc\uadf8\u00b7\uc774\ud0e4\ub9ad = \uc640\uc774\uc5b4 \uc8fc\uc11d\u00b7\uc2a4\ud399"
    ),
    "screen_row_legend_title": u(
        "\u25a0 \ud589\ubcc4 screenId \u00b7 \uae30\ub2a5 \uc694\uc57d (\uc640\uc774\uc5b4 \uc8fc\uc11d, \ud45c \uc5f4\uc5d0 \ub123\uc9c0 \ub9d0)"
    ),
    "screen_row_legend_scope_pointer": u(
        "\u203b \u300c\uc870\ud68c \ubc94\uc704\u300d \uc5f4 \ud654\uba74\ubcc4 \uc758\ubbf8\ub294 \uc544\ub798 \uad6c\ubd84\uc120 \uc774\ud6c4 "
        "\ube14\ub85d #scope-wire-legend-req02\ub97c \ucc38\uace0."
    ),
    "screen_row_legend_pb": u("pb-feplog \u2014 \uc694\uc57d: \uc811\uadfc + \ubcf5\ud638\ud654(\uc635\uc158)"),
    "screen_row_legend_java": u("java-fw-imagelog \u2014 \uc694\uc57d: \uc811\uadfc + \ubcf5\ud638\ud654(\uc635\uc158)"),
    "screen_row_legend_act": u("activity-log \u2014 \uc694\uc57d: \uc811\uadfc + \ubaa9\ub85d\ubc94\uc704"),
    "screen_row_legend_sh": u("search-history \u2014 \uc694\uc57d: \ubaa9\ub85d\ubc94\uc704 + \uc2b9\uc778"),
    "screen_row_legend_pend": u("pending-approvals \u2014 \uc694\uc57d: \ubaa9\ub85d\ubc94\uc704 + \uc2b9\uc778"),
    "screen_row_legend_stat": u("statistics \u2014 \uc694\uc57d: \uc811\uadfc + \ubaa9\ub85d\ubc94\uc704"),
    "screen_row_legend_um": u("user-management \u2014 \uc694\uc57d: \uc811\uadfc + \uc218\uc815(\uc635\uc158)"),
    "screen_row_legend_pgm": u("permission-group-management \u2014 \uc694\uc57d: \uc811\uadfc + \uc218\uc815(\uc635\uc158)"),
    "screen_row_legend_dept": u(
        "department-approvers \u2014 \uc608\uc2dc: \uc774 \uadf8\ub8f9\uc5d0 \uc5c6\ub294 \ud654\uba74 \u2192 \uba54\ub274\u00b7API \ubbf8\uc0ac\uc6a9, \ud589 \uc5c6\uc74c"
    ),
    "um_group_col_wire": u(
        "\uc640\uc774\uc5b4: \uad8c\ud55c \uadf8\ub8f9 \uc5f4 = \ub2e8\uc77c \uc120\ud0dd(single-select); "
        "data-grid-select-primitive / common-grid-cell-select.svg"
    ),
    "tc09_api_foot": u(
        "API: .../departments/user-permission-hierarchy \u00b7 \uc0ac\uc6a9\uc790 \ubaa9\ub85d GET .../users "
        "(\uacbd\ub85c\ub294 \uc694\uc57d; SoT docs/contract.md \ubc0f specs)"
    ),
    "fe_banner_wire_note": u(
        "\uc640\uc774\uc5b4 \uc8fc\uc11d: \uc774 \uad6c\uc5ed\uc740 FE \ub77c\uc6b0\ud305\u00b7\ub808\uc774\uc544\uc6c3 \uacbd\uace0\uc6a9. "
        "user-management / user-permission-hierarchy\ub294 \ub8e8\ud2b8 data-frontend-note\uc640 \uac19\uc774 \ub3d9\uc77c UserManagement \ucef4\ud3ec\ub10c\ud2b8 \ub80c\ub354."
    ),
    "wf_annotations_zone_title": u("\uc640\uc774\uc5b4 \uc8fc\uc11d \uad6c\uc5ed (\uc81c\ud488 \ud654\uba74 \ubaa9\uc5c5 \uc544\ub2d8)"),
}


def wf_annotation_zone_layout(main_content_bottom: int) -> tuple[int, int]:
    """Gutter below mock, then header strip; returns (zone_y, first_body_y)."""
    gap = 8
    header = 28
    zone_y = main_content_bottom + gap
    anno_y0 = zone_y + header
    return zone_y, anno_y0


def _wireframe_authoring_styles() -> str:
    """Shared: root legend box + dashed annotation surfaces vs mock UI."""
    return """      .wf-layout-main { /* shell + product mock only */ }
      .wf-layout-wire-annotations { /* blocks below main viewport + root legend */ }
      .wf-layout-wire-annotations text { text-anchor: start; }
      .wf-annotations-zone-panel { fill: #dfe4ea; stroke: none; }
      .wf-annotations-zone-title { fill: #37474f; font-family: system-ui, "Noto Sans KR", sans-serif; font-size: 11px; font-weight: 700; }
      .wf-meta-box { fill: #fafafa; stroke: #78909c; stroke-width: 1; stroke-dasharray: 5 3; }
      .wf-meta-title { fill: #37474f; font-family: system-ui, sans-serif; font-size: 10px; font-weight: 700; }
      .wf-meta-line { fill: #546e7a; font-family: system-ui, sans-serif; font-size: 8px; }
      .wf-anno-tag { fill: #455a64; font-family: system-ui, sans-serif; font-size: 7px; font-weight: 700; }
      .wf-mock-tag { fill: #616161; font-family: system-ui, sans-serif; font-size: 6px; font-weight: 600; }
      .wf-anno-text { fill: #546e7a; font-family: system-ui, sans-serif; font-style: italic; }
      .wf-anno-legend { fill: #607d8b; font-family: system-ui, sans-serif; font-size: 8px; font-weight: 600; }
      .wf-anno-surf { stroke-dasharray: 5 4; stroke-width: 1.25; stroke: #78909c; }
      .wf-anno-fill-light { fill: #f8fafc; }
      .wf-anno-fill-amber { fill: #fffdf7; }
      .wf-anno-fill-purple { fill: #faf7fc; }
      .wf-anno-fill-red { fill: #fff5f5; }
      .ux-banner.wf-anno-surf { fill: #f3edf8; stroke: #78909c; stroke-width: 1.25; }
      .note-box.wf-anno-surf { fill: #fffdf5; stroke: #78909c; stroke-width: 1.25; }
      .callout.wf-anno-surf { fill: #f2f6fa; stroke: #78909c; stroke-width: 1.25; }
      .banner.wf-anno-surf { fill: #fffaf3; stroke: #78909c; stroke-width: 1.25; }
      .warn.wf-anno-surf { fill: #fff5f5; stroke: #78909c; stroke-width: 1.25; }
      .card.wf-anno-surf { fill: #f7f9fa; stroke: #78909c; stroke-width: 1.25; }
      .wf-scope-legend-title { fill: #37474f; font-family: system-ui, "Noto Sans KR", sans-serif; font-size: 8px; font-weight: 700; }
      .wf-scope-legend-line { fill: #546e7a; font-family: system-ui, "Noto Sans KR", sans-serif; font-size: 6.5px; font-style: italic; }
      .wf-section-divider { stroke: #90a4ae; stroke-width: 1; stroke-dasharray: 6 4; }
"""


def wireframe_chrome_css() -> str:
    """Toggle via root data-wireframe-chrome: full | no-meta | clean (no-meta = hide root summary box)."""
    return """      svg[data-wireframe-chrome="no-meta"] .wf-chrome-meta { display: none !important; }
      svg[data-wireframe-chrome="clean"] .wf-layout-wire-annotations,
      svg[data-wireframe-chrome="clean"] .wf-chrome-meta,
      svg[data-wireframe-chrome="clean"] .wf-chrome-annotations { display: none !important; }
      svg[data-wireframe-chrome="clean"] .ux-banner.wf-anno-surf,
      svg[data-wireframe-chrome="clean"] .note-box.wf-anno-surf,
      svg[data-wireframe-chrome="clean"] .banner.wf-anno-surf,
      svg[data-wireframe-chrome="clean"] .warn.wf-anno-surf,
      svg[data-wireframe-chrome="clean"] .callout.wf-anno-surf { display: none !important; }
      svg[data-wireframe-chrome="clean"] .card.wf-anno-surf { stroke-dasharray: none !important; stroke: #cfd8dc !important; fill: #ffffff !important; }
      svg[data-wireframe-chrome="clean"] .wf-annotations-zone-panel,
      svg[data-wireframe-chrome="clean"] .wf-annotations-zone-divider { display: none !important; }
"""


def wf_anno_tag(x: int, y: int, suffix: str) -> str:
    t = escape(L["wf_anno_tag"], entities={'"': "&quot;"})
    return f'  <text x="{x}" y="{y}" class="wf-anno-tag wf-chrome-annotations" id="wf-anno-tag-{suffix}">{t}</text>'


def wf_mock_ui_tag(x: int, y: int, suffix: str) -> str:
    t = escape(L["wf_mock_ui_tag"], entities={'"': "&quot;"})
    return f'  <text x="{x}" y="{y}" class="wf-mock-tag wf-chrome-annotations" id="wf-mock-ui-tag-{suffix}">{t}</text>'


def wf_section_divider(
    y: int, uid: str, x1: int = WIRE_ANNO_TEXT_X, x2: int = 1152
) -> str:
    """Horizontal rule between wire-annotation themes (single slab; no inner boxes)."""
    return (
        f'  <line class="wf-section-divider wf-chrome-annotations" x1="{x1}" y1="{y}" x2="{x2}" y2="{y}" '
        f'aria-hidden="true" id="wf-section-divider-{uid}"/>'
    )


def wire_annotations_stack(
    uid: str,
    inner: str,
    *,
    zone_y: int,
    view_h: int,
    zone_w: int = 1200,
    anno_text_x: int = WIRE_ANNO_TEXT_X,
) -> str:
    """Non-main wire copy on a separate visible slab (below product mock)."""
    h = max(0, view_h - zone_y)
    title_esc = escape(L["wf_annotations_zone_title"], entities={'"': "&quot;"})
    slab = (
        f'  <rect class="wf-annotations-zone-panel wf-chrome-annotations" x="0" y="{zone_y}" '
        f'width="{zone_w}" height="{h}" id="wf-annotations-zone-bg-{uid}"/>'
    )
    divider = (
        f'  <line class="wf-annotations-zone-divider wf-chrome-annotations" x1="0" y1="{zone_y}" '
        f'x2="{zone_w}" y2="{zone_y}" stroke="#455a64" stroke-width="2"/>'
    )
    label = (
        f'  <text x="{anno_text_x}" y="{zone_y + 15}" class="wf-annotations-zone-title wf-chrome-annotations" '
        f'id="wf-annotations-zone-hint-{uid}">{title_esc}</text>'
    )
    return (
        f'  <g id="wf-wire-annotations-{uid}" class="wf-layout-wire-annotations" data-wf-layer="wire-annotations">\n'
        f"{slab}\n{divider}\n{label}\n{inner}\n"
        f"  </g>"
    )


def wireframe_visible_meta_panel(
    x: int,
    y: int,
    w: int,
    h: int,
    uid: str,
    *,
    sep_y: int | None = None,
    sep_x1: int = WIRE_ANNO_TEXT_X,
    sep_x2: int = 1152,
) -> str:
    """Root data-* summary as plain text in the wire slab (no inner rect). Toggle: .wf-chrome-meta. `h` unused (API compat)."""
    keys = ("meta_vis_title", "meta_vis_l1", "meta_vis_l2", "meta_vis_l3", "meta_vis_l4")
    t0, t1, t2, t3, t4 = (escape(L[k], entities={'"': "&quot;"}) for k in keys)
    sep = ""
    if sep_y is not None:
        sep = (
            f'  <line class="wf-chrome-meta wf-chrome-annotations wf-meta-separator" x1="{sep_x1}" '
            f'y1="{sep_y}" x2="{sep_x2}" y2="{sep_y}" stroke="#90a4ae" stroke-width="1" '
            f'stroke-dasharray="6 4" aria-hidden="true"/>\n'
        )
    return (
        sep
        + f"""  <g class="wf-chrome-meta wf-chrome-annotations wf-root-legend wireframe-root-legend" id="wireframe-visible-meta-{uid}" data-wf-layer="wire-annotations-root" role="region" aria-label="Wireframe annotations: root data-* summary; not product UI. Toggle data-wireframe-chrome">
    <title>Wireframe annotations — root summary</title>
    <text x="{x}" y="{y + 12}" class="wf-meta-title" id="wireframe-visible-meta-title-{uid}" text-anchor="start">{t0}</text>
    <text x="{x}" y="{y + 24}" class="wf-meta-line" id="wireframe-visible-meta-l1-{uid}" text-anchor="start">{t1}</text>
    <text x="{x}" y="{y + 36}" class="wf-meta-line" id="wireframe-visible-meta-l2-{uid}" text-anchor="start">{t2}</text>
    <text x="{x}" y="{y + 48}" class="wf-meta-line" id="wireframe-visible-meta-l3-{uid}" text-anchor="start">{t3}</text>
    <text x="{x}" y="{y + 60}" class="wf-meta-line" id="wireframe-visible-meta-l4-{uid}" text-anchor="start">{t4}</text>
  </g>"""
    )


def wireframe_visible_meta_panel_height() -> int:
    """Vertical space for meta text block (no box)."""
    return 72


def scope_column_wire_legend_height() -> int:
    n = 12
    line_h = 10.5
    return int(18 + (n - 1) * line_h + 8)


def scope_column_wire_legend(left_x: int, y: int, w: int, h: int, uid: str) -> str:
    """Visible wire annotation: 조회 범위 lines only (no inner box; single slab)."""
    keys = (
        "scope_wire_legend_title",
        "scope_wire_legend_row_pointer",
        "scope_wire_legend_col",
        "scope_wire_legend_pb",
        "scope_wire_legend_java",
        "scope_wire_legend_act",
        "scope_wire_legend_sh",
        "scope_wire_legend_pend",
        "scope_wire_legend_stat",
        "scope_wire_legend_um_pgm",
        "scope_wire_legend_dept",
        "scope_wire_legend_primitive",
    )
    lines = [escape(L[k], entities={'"': "&quot;"}) for k in keys]
    line_h = 10.5
    y0 = y + 16
    texts: list[str] = []
    for i, esc in enumerate(lines):
        cls = (
            "wf-scope-legend-title wf-chrome-annotations"
            if i == 0
            else "wf-scope-legend-line wf-chrome-annotations"
        )
        fs = 8 if i == 0 else 6.5
        yi = y0 + int(round(i * line_h))
        texts.append(
            f'  <text x="{left_x}" y="{yi}" class="{cls}" font-size="{fs}px" text-anchor="start">{esc}</text>'
        )
    inner = "\n".join(texts)
    return (
        f'  <g id="scope-wire-legend-{uid}" class="wf-chrome-annotations" '
        f'data-scope-wire-legend="1" role="group" aria-label="Scope column wire annotation">\n'
        f"{inner}\n"
        f"  </g>"
    )


def screen_row_wire_legend_height() -> int:
    n = 10
    line_h = 10.5
    return int(18 + (n - 1) * line_h + 8)


def screen_row_wire_legend(left_x: int, y: int, w: int, h: int, uid: str) -> str:
    """Visible wire annotation: per-row screenId summaries (no inner box)."""
    keys = (
        "screen_row_legend_title",
        "screen_row_legend_scope_pointer",
        "screen_row_legend_pb",
        "screen_row_legend_java",
        "screen_row_legend_act",
        "screen_row_legend_sh",
        "screen_row_legend_pend",
        "screen_row_legend_stat",
        "screen_row_legend_um",
        "screen_row_legend_pgm",
        "screen_row_legend_dept",
    )
    lines = [escape(L[k], entities={'"': "&quot;"}) for k in keys]
    line_h = 10.5
    y0 = y + 16
    texts: list[str] = []
    for i, esc in enumerate(lines):
        cls = "wf-scope-legend-title wf-chrome-annotations" if i == 0 else "wf-scope-legend-line wf-chrome-annotations"
        fs = 8 if i == 0 else 6.5
        yi = y0 + int(round(i * line_h))
        texts.append(
            f'  <text x="{left_x}" y="{yi}" class="{cls}" font-size="{fs}px" text-anchor="start">{esc}</text>'
        )
    inner = "\n".join(texts)
    return (
        f'  <g id="screen-row-wire-legend-{uid}" class="wf-chrome-annotations" '
        f'data-screen-row-wire-legend="1" role="group" aria-label="Per-row screenId wire annotation">\n'
        f"{inner}\n"
        f"  </g>"
    )


def _wrap_text_lines(s: str, max_chars: int) -> list[str]:
    """Greedy wrap for long annotation strings (mixed KO/EN; split on spaces when possible)."""
    lines: list[str] = []
    rest = s.strip()
    while rest:
        if len(rest) <= max_chars:
            lines.append(rest)
            break
        chunk = rest[:max_chars]
        br = chunk.rfind(" ")
        if br > max_chars // 2:
            lines.append(rest[:br])
            rest = rest[br + 1 :].lstrip()
        else:
            lines.append(rest[:max_chars])
            rest = rest[max_chars:].lstrip()
    return lines


def svg1() -> str:
    """User management wire: product layout in white card; wf-chrome-annotations only below card."""
    CONTENT_TOP = 80
    CONTENT_BOTTOM = 588
    CONTENT_H = CONTENT_BOTTOM - CONTENT_TOP
    zone_y, ANNO_Y0 = wf_annotation_zone_layout(CONTENT_BOTTOM)
    e = lambda t: escape(t, entities={'"': "&quot;", "&": "&amp;"})

    y = ANNO_Y0
    dock1: list[str] = []
    for i, ln in enumerate(_wrap_text_lines(L["hint_um"], 88)):
        hid = "view-hint" if i == 0 else f"view-hint-l{i + 1}"
        dock1.append(
            f'  <text x="288" y="{y + 11}" class="hint wf-anno-text wf-chrome-annotations" '
            f'id="{hid}">{e(ln)}</text>'
        )
        y += 16
    y += 4
    dock1.append(
        f'  <text x="288" y="{y + 11}" class="wf-scope-legend-line wf-chrome-annotations" '
        f'font-size="6.5px" id="um-col-group-wire">{e(L["um_group_col_wire"])}</text>'
    )
    y += 22
    dock1.append(wf_section_divider(y, "req01-strike"))
    y += 10
    dock1.append(
        f'  <text x="288" y="{y + 11}" class="strike-label wf-chrome-annotations" id="rm-col-note">'
        f'{e(L["rm_col"])}</text>'
    )
    y += 18
    dock1.append(
        f'  <text x="288" y="{y + 11}" class="strike-label wf-chrome-annotations" id="rm-flag-note">'
        f'{e(L["rm_flag"])}</text>'
    )
    y += 22
    dock1.append(wf_section_divider(y, "req01-dev"))
    y += 10
    dock1.append(
        f'  <text x="288" y="{y + 12}" class="callout-title wf-chrome-annotations" id="dev-note-heading">'
        f'{e(L["dev_note"])}</text>'
    )
    y += 18
    dock1.append(
        f'  <text x="288" y="{y + 11}" class="callout-body wf-chrome-annotations" id="dev-b1">'
        f'{e(L["dev_b1"])}</text>'
    )
    y += 18
    dock1.append(
        f'  <text x="288" y="{y + 11}" class="callout-body wf-chrome-annotations" id="dev-b2">'
        f'{e(L["dev_b2"])}</text>'
    )
    y += 18
    dock1.append(
        f'  <text x="288" y="{y + 11}" class="callout-body wf-chrome-annotations" id="dev-b3">'
        f'{e(L["dev_b3"])}</text>'
    )
    y += 20
    META_SEP_Y = y
    META_Y = META_SEP_Y + 10
    VIEW_H = META_Y + wireframe_visible_meta_panel_height() + 24
    svg1_annotation_dock = "\n".join(dock1)
    wire_stack_req01 = wire_annotations_stack(
        "req01",
        f"{svg1_annotation_dock}\n{wireframe_visible_meta_panel(WIRE_ANNO_TEXT_X, META_Y, 1104, 70, 'req01', sep_y=META_SEP_Y)}",
        zone_y=zone_y,
        view_h=VIEW_H,
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 {VIEW_H}" role="img" aria-label="Final target user management without decrypt approver column req 20260323" data-req="20260323-approver-eligibility" data-tc="TC-13" data-wireframe-semantics="v1" data-wireframe-rules-ref=".cursor/rules/svg-wireframe-semantics.mdc" data-wireframe-visible-meta="wireframe-visible-meta-req01" data-wireframe-chrome="full" data-ref="frontend/src/components/UserManagement/UserManagement.js" data-grid-select-primitive="assets/svg/primitives/common-grid-cell-select.svg" data-col-screen-name-policy="na-user-table">
  <defs>
    <style>
      .view-title {{ fill: #212121; font-family: "Noto Sans KR", "Roboto", system-ui, sans-serif; font-size: 24px; font-weight: 600; }}
      .bg {{ fill: #f5f5f5; }}
      .side {{ fill: #ffffff; stroke: #e0e0e0; stroke-width: 1; }}
      .bar {{ fill: #1976d2; }}
      .bart {{ fill: #ffffff; font-family: system-ui, sans-serif; font-size: 14px; }}
      .navg {{ fill: #616161; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
      .nav {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 12px; }}
      .navhi {{ fill: #1976d2; font-weight: 600; }}
      .paper {{ fill: #ffffff; stroke: #e0e0e0; stroke-width: 1; }}
      .hint {{ fill: #666666; font-family: system-ui, sans-serif; font-size: 13px; }}
      .tree-box {{ fill: #fafafa; stroke: #dddddd; stroke-width: 1; }}
      .tree-toggle {{ fill: #555555; font-family: system-ui, sans-serif; font-size: 11px; }}
      .node {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 12px; }}
      .node-strong {{ fill: #1976d2; font-family: system-ui, sans-serif; font-size: 12px; font-weight: 600; }}
      .hdr-bg {{ fill: #f5f5f5; stroke: #dddddd; stroke-width: 1; }}
      .hdr {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
      .col-band {{ fill: #f6f8fb; }}
      .col-band-alt {{ fill: #f1f4f8; }}
      .row {{ fill: rgba(255, 255, 255, 0); stroke: #e3e8ef; stroke-width: 1; }}
      .row-alt {{ fill: rgba(255, 255, 255, 0); stroke: #e3e8ef; stroke-width: 1; }}
      .grid-select-field {{ fill: #ffffff; stroke: #bdbdbd; stroke-width: 1; }}
      .grid-select-txt {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 11px; }}
      .grid-select-chev {{ fill: #757575; }}
      .callout {{ fill: #e3f2fd; stroke: #1976d2; stroke-width: 2; }}
      .callout-title {{ fill: #0d47a1; font-family: system-ui, sans-serif; font-size: 12px; font-weight: 600; }}
      .callout-body {{ fill: #1565c0; font-family: system-ui, sans-serif; font-size: 11px; }}
      .strike-label {{ fill: #c62828; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
{_wireframe_authoring_styles()}
{wireframe_chrome_css()}
    </style>
  </defs>
  <g id="wf-main-layout-req01" class="wf-layout-main" data-wf-layer="main">
  <rect class="bg" width="1200" height="{zone_y}" id="shell-bg"/>
  <rect class="side" x="0" y="0" width="240" height="{zone_y}" id="shell-sidebar"/>
  <text x="16" y="340" class="navg" id="nav-group-admin">{L["admin"]}</text>
  <text x="24" y="366" class="nav navhi" id="nav-users">{L["user_mgmt"]}</text>
  <text x="24" y="392" class="nav" id="nav-groups">{L["perm_groups"]}</text>
  <rect class="bar" x="240" y="0" width="960" height="56" id="shell-appbar"/>
  <text x="260" y="36" class="bart" id="appbar-title">{L["app_title"]}</text>
  <rect class="paper" x="264" y="80" width="912" height="{CONTENT_H}" rx="4" id="content-panel"/>
  <text x="288" y="122" class="view-title" id="view-title">{L["user_final"]}</text>
  <rect class="tree-box" x="288" y="164" width="864" height="400" rx="4" id="tree-section"/>
  <text x="304" y="194" class="tree-toggle">▼</text>
  <text x="322" y="194" class="node-strong" id="dept-node-ops">{L["ops_dept"]}</text>
  <rect class="col-band" x="338" y="210" width="100" height="98" id="users-col-name"/>
  <rect class="col-band-alt" x="438" y="210" width="150" height="98" id="users-col-id"/>
  <rect class="col-band" x="588" y="210" width="80" height="98" id="users-col-grade"/>
  <rect class="col-band" x="668" y="210" width="140" height="98" id="users-col-position"/>
  <rect class="col-band-alt" x="808" y="210" width="320" height="98" id="users-col-group"/>
  <rect class="hdr-bg" x="338" y="210" width="790" height="30" id="users-table-header"/>
  <text x="350" y="229" class="hdr">{L["col_name"]}</text>
  <text x="450" y="229" class="hdr">{L["col_uid"]}</text>
  <text x="600" y="229" class="hdr">{L["col_grade"]}</text>
  <text x="680" y="229" class="hdr">{L["col_title"]}</text>
  <text x="820" y="229" class="hdr">{L["col_group"]}</text>
  <rect class="row" x="338" y="240" width="790" height="34" id="users-row-1"/>
  <text x="350" y="261" class="node">{L["hong"]}</text>
  <text x="450" y="261" class="node">20260001</text>
  <text x="600" y="261" class="node">{L["team_lead"]}</text>
  <text x="680" y="261" class="node">{L["lead"]}</text>
  <g id="grid-cell-select-row1" aria-label="perm group select">
    <rect class="grid-select-field" x="848" y="246" width="120" height="22" rx="2" id="group-select-field-1"/>
    <text x="853" y="261" class="grid-select-txt" id="group-select-value-1">{L["grp_ops"]}</text>
    <path class="grid-select-chev" d="M 950 253 L 958 260 L 942 260 Z" id="group-select-chevron-1" aria-hidden="true"/>
  </g>
  <rect class="row-alt" x="338" y="274" width="790" height="34" id="users-row-2"/>
  <text x="350" y="295" class="node">{L["kim"]}</text>
  <text x="450" y="295" class="node">20260002</text>
  <text x="600" y="295" class="node">{L["deputy"]}</text>
  <text x="680" y="295" class="node">{L["engineer"]}</text>
  <g id="grid-cell-select-row2" aria-label="perm group select">
    <rect class="grid-select-field" x="848" y="280" width="120" height="22" rx="2" id="group-select-field-2"/>
    <text x="853" y="295" class="grid-select-txt" id="group-select-value-2">{L["grp_dev"]}</text>
    <path class="grid-select-chev" d="M 950 287 L 958 294 L 942 294 Z" id="group-select-chevron-2" aria-hidden="true"/>
  </g>
  </g>
{wire_stack_req01}
</svg>
"""


def svg2() -> str:
    """Screen-function matrix + UX wireframe (banner, context, row summary, snackbar). No app code."""
    LP_X, LP_W = 288, 188
    GAP = 12
    RP_X = LP_X + LP_W + GAP
    RP_W = 864 - LP_W - GAP
    TX = RP_X + 10
    TW = RP_W - 20
    # Scope column: narrow cell for 2 Hangul chars + chevron (not full common-grid-cell-select 120px).
    W_CAT, W_SCR, W_ACC, W_SCOPE, W_WR, W_AP, W_DEC = 56, 216, 52, 62, 64, 108, 86
    assert W_CAT + W_SCR + W_ACC + W_SCOPE + W_WR + W_AP + W_DEC == TW
    C0 = TX
    C1 = C0 + W_CAT
    C2 = C1 + W_SCR
    C3 = C2 + W_ACC
    C4 = C3 + W_SCOPE
    C5 = C4 + W_WR
    C6 = C5 + W_AP
    CX_ACC = C2 + W_ACC // 2
    CX_SCOPE_HDR = C3 + W_SCOPE // 2
    CX_WR = C4 + W_WR // 2
    CX_AP = C5 + W_AP // 2
    CX_DEC = C6 + W_DEC // 2
    # Tight layout: product title + panels/matrix/snack/save only inside white card; all wf-chrome-annotations below.
    PANEL_Y = 124
    HY = PANEL_Y + 12
    RH = 34
    Y0 = HY + 28

    def y_row(i: int) -> int:
        return Y0 + i * RH

    y_end = y_row(8) + RH
    PY = PANEL_Y
    CB_SZ = 14

    def cb_top(i: int) -> int:
        return y_row(i) + (RH - CB_SZ) // 2

    # Compact scope dropdown: ~2 Hangul glyphs; chevron right-aligned inside cell.
    DD_H = 22
    DD_W = 52
    DD_X = C3 + (W_SCOPE - DD_W) // 2
    DD_CHEV = DD_X + DD_W - 12  # small ▼ triangle, right inset

    def dd_top(i: int) -> int:
        return y_row(i) + (RH - DD_H) // 2

    def scope_cell_select_lines(row_i: int, field_id: str, val_id: str, *, locked: bool = False) -> str:
        dt = dd_top(row_i)
        lock_cls = " is-locked" if locked else ""
        return (
            f'  <rect class="grid-select-field{lock_cls}" x="{DD_X}" y="{dt}" width="{DD_W}" height="{DD_H}" rx="2" id="{field_id}"/>\n'
            f'  <text x="{DD_X + 4}" y="{dt + 15}" class="grid-select-txt" id="{val_id}">{L["scope_dd_value"]}</text>\n'
            f'  <path class="grid-select-chev" d="M {DD_CHEV} {dt + 6} L {DD_CHEV + 8} {dt + 6} L {DD_CHEV + 4} {dt + 13} Z" aria-hidden="true"/>'
        )

    CONTENT_TOP = 72
    SNACK_Y = y_end + 14
    SAVE_Y = SNACK_Y + 46
    CONTENT_PANEL_BOTTOM = SAVE_Y + 52
    CONTENT_PANEL_H = CONTENT_PANEL_BOTTOM - CONTENT_TOP
    PANEL_H = CONTENT_PANEL_BOTTOM - PY

    LEGEND_H = scope_column_wire_legend_height()
    ROW_LEGEND_H = screen_row_wire_legend_height()
    zone_y, ANNO_Y0 = wf_annotation_zone_layout(CONTENT_PANEL_BOTTOM)
    sc_scope_act = scope_cell_select_lines(2, "scope-dd-act", "scope-dd-act-val")
    sc_scope_sh = scope_cell_select_lines(3, "scope-dd-sh", "scope-dd-sh-val")
    sc_scope_pend = scope_cell_select_lines(
        4, "scope-dd-pend-locked", "scope-dd-pend-val", locked=True
    )
    sc_scope_stat = scope_cell_select_lines(5, "scope-dd-stat", "scope-dd-stat-val")
    e = lambda t: escape(t, entities={'"': "&quot;", "&": "&amp;"})

    dock: list[str] = []
    yd = ANNO_Y0
    for i, ln in enumerate(_wrap_text_lines(L["pg_sub"], 90)):
        tid = "subtitle" if i == 0 else f"subtitle-l{i + 1}"
        dock.append(
            f'  <text x="288" y="{yd + 11}" class="lbl wf-anno-text wf-chrome-annotations" '
            f'font-size="8px" id="{tid}">{e(ln)}</text>'
        )
        yd += 11
    yd += 6
    dock.append(
        f'  <text x="288" y="{yd + 11}" class="wf-anno-legend wf-chrome-annotations" font-size="8px" '
        f'id="wf-legend-matrix">{e(L["wf_legend_short"])}</text>'
    )
    yd += 16
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{yd + 11}" class="lbl wf-anno-text wf-chrome-annotations" '
        f'font-size="8px" id="pg-matrix-layout-note">{e(L["pg_matrix_layout_note"])}</text>'
    )
    yd += 22
    dock.append(wf_section_divider(yd, "req02-s1"))
    yd += 10
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{yd + 11}" class="ux-banner-t wf-chrome-annotations" id="ux-banner-title">'
        f'{e(L["ux_banner_t"])}</text>'
    )
    yd += 18
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{yd + 11}" class="ux-banner-b wf-chrome-annotations" id="ux-b1">\u2022 {e(L["ux_b1"])}</text>'
    )
    yd += 12
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{yd + 11}" class="ux-banner-b wf-chrome-annotations" id="ux-b2">\u2022 {e(L["ux_b2"])}</text>'
    )
    yd += 12
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{yd + 11}" class="ux-banner-b wf-chrome-annotations" id="ux-b3">\u2022 {e(L["ux_b3"])}</text>'
    )
    yd += 12
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{yd + 11}" class="ux-banner-b wf-chrome-annotations" id="ux-b4">\u2022 {e(L["ux_b4"])}</text>'
    )
    yd += 16
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{yd + 11}" class="screen-id wf-anno-text wf-chrome-annotations" '
        f'id="list-mui-note">{e(L["list_mui_note"])}</text>'
    )
    yd += 20
    dock.append(wf_section_divider(yd, "req02-s2"))
    yd += 10
    dock.append(screen_row_wire_legend(WIRE_ANNO_TEXT_X, yd, TW, ROW_LEGEND_H, "req02"))
    yd += ROW_LEGEND_H + 8
    dock.append(wf_section_divider(yd, "req02-s3"))
    yd += 10
    dock.append(scope_column_wire_legend(WIRE_ANNO_TEXT_X, yd, TW, LEGEND_H, "req02"))
    yd += LEGEND_H + 10
    dock.append(wf_section_divider(yd, "req02-s4"))
    yd += 10
    FOOT_Y0 = yd
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{FOOT_Y0 + 16}" class="foot wf-anno-text wf-chrome-annotations" '
        f'id="pg-foot-ui">{e(L["pg_foot_ui"])}</text>'
    )
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{FOOT_Y0 + 32}" class="foot wf-anno-text wf-chrome-annotations" '
        f'id="pg-foot-spec">{e(L["pg_foot_spec"])}</text>'
    )
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{FOOT_Y0 + 48}" class="foot wf-anno-text wf-chrome-annotations" '
        f'id="pg-foot-extra">{e(L["pg_foot_extra"])}</text>'
    )
    dock.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{FOOT_Y0 + 64}" class="foot wf-anno-text wf-chrome-annotations" '
        f'id="pg-foot-frontend-agent">{e(L["pg_foot_fe"])}</text>'
    )
    yd = FOOT_Y0 + 78
    dock.append(wf_section_divider(yd, "req02-s5"))
    yd += 10
    NOTE_Y = yd
    dock.append(
        f'  <text x="288" y="{NOTE_Y + 11}" class="note-t wf-chrome-annotations" id="policy-notes-title">'
        f'{e(L["note_t"])}</text>'
    )
    yd = NOTE_Y + 22
    dock.append(
        f'  <text x="288" y="{yd + 11}" class="note-b wf-chrome-annotations" id="policy-n1">{e(L["n1"])}</text>'
    )
    yd += 14
    dock.append(
        f'  <text x="288" y="{yd + 11}" class="note-b wf-chrome-annotations" id="policy-n2">{e(L["n2"])}</text>'
    )
    yd += 14
    dock.append(
        f'  <text x="288" y="{yd + 11}" class="note-b wf-chrome-annotations" id="policy-n3">{e(L["n3"])}</text>'
    )
    yd += 14
    dock.append(
        f'  <text x="288" y="{yd + 11}" class="note-b wf-chrome-annotations" id="policy-n4">{e(L["n4"])}</text>'
    )
    yd += 14
    dock.append(
        f'  <text x="288" y="{yd + 11}" class="note-b wf-chrome-annotations" id="policy-n5">{e(L["n5"])}</text>'
    )
    yd += 14
    dock.append(
        f'  <text x="288" y="{yd + 11}" class="note-b wf-chrome-annotations" id="policy-n6">{e(L["n6"])}</text>'
    )
    yd += 18
    META_SEP_Y = yd
    META_Y = META_SEP_Y + 10
    VIEW_H = META_Y + wireframe_visible_meta_panel_height() + 24
    svg2_annotation_dock = "\n".join(dock)
    wire_stack_req02 = wire_annotations_stack(
        "req02",
        f"{svg2_annotation_dock}\n{wireframe_visible_meta_panel(WIRE_ANNO_TEXT_X, META_Y, 1104, 70, 'req02', sep_y=META_SEP_Y)}",
        zone_y=zone_y,
        view_h=VIEW_H,
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 {VIEW_H}" role="img" aria-label="Permission group screen functions matrix ScreenSelectionTree spec 1.1.1" data-req="20260323-approver-eligibility" data-ref="frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js" data-wireframe-semantics="v1" data-wireframe-rules-ref=".cursor/rules/svg-wireframe-semantics.mdc" data-wireframe-visible-meta="wireframe-visible-meta-req02" data-wireframe-chrome="full" data-layout="two-pane-group-list-matrix" data-matrix-pagination="none" data-group-list-actions="add-delete" data-scope-cell="compact-2char" data-scope-wire-legend="scope-wire-legend-req02" data-screen-row-wire-legend="screen-row-wire-legend-req02" data-grid-checkbox-primitive="assets/svg/primitives/common-grid-checkbox-14.svg" data-grid-checkbox-size="{CB_SZ}" data-grid-select-primitive="assets/svg/primitives/common-grid-cell-select.svg" data-col-screen-name-policy="user-facing-label-screenId-in-visible-wire-layer">
  <defs>
    <style>
      .view-title {{ fill: #212121; font-family: "Noto Sans KR", "Roboto", system-ui, sans-serif; font-size: 22px; font-weight: 600; }}
      .bg {{ fill: #f5f5f5; }}
      .side {{ fill: #ffffff; stroke: #e0e0e0; stroke-width: 1; }}
      .bar {{ fill: #1976d2; }}
      .bart {{ fill: #ffffff; font-family: system-ui, sans-serif; font-size: 14px; }}
      .navg {{ fill: #616161; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
      .nav {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 12px; }}
      .navhi {{ fill: #1976d2; font-weight: 600; }}
      .paper {{ fill: #ffffff; stroke: #e0e0e0; stroke-width: 1; }}
      .lbl {{ fill: #616161; font-family: system-ui, sans-serif; font-size: 11px; }}
      .item {{ fill: #37474f; font-family: system-ui, sans-serif; font-size: 11px; }}
      .hdr {{ fill: #37474f; font-family: system-ui, sans-serif; font-size: 9px; font-weight: 700; }}
      .hdr-mid {{ fill: #37474f; font-family: system-ui, sans-serif; font-size: 9px; font-weight: 700; text-anchor: middle; }}
      .row-bg {{ fill: #fafbfc; stroke: #e8eaed; stroke-width: 1; }}
      .row-bg-alt {{ fill: #ffffff; stroke: #e8eaed; stroke-width: 1; }}
      .screen-id {{ fill: #78909c; font-family: ui-monospace, monospace; font-size: 8px; }}
      .tiny {{ fill: #455a64; font-family: system-ui, sans-serif; font-size: 8px; }}
      .tiny-mid {{ fill: #455a64; font-family: system-ui, sans-serif; font-size: 8px; text-anchor: middle; }}
      .note-box {{ fill: #fff8e1; stroke: #ff8f00; stroke-width: 1; }}
      .note-t {{ fill: #e65100; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
      .note-b {{ fill: #5d4037; font-family: system-ui, sans-serif; font-size: 9px; }}
      .foot {{ fill: #455a64; font-family: system-ui, sans-serif; font-size: 9px; }}
      .hdr-row {{ fill: #eceff1; stroke: #b0bec5; stroke-width: 1; }}
      .grid-frame {{ fill: none; stroke: #90a4ae; stroke-width: 1.25; }}
      .vline {{ stroke: #eceff1; stroke-width: 1; }}
      .group-strip {{ stroke-width: 0; opacity: 0.9; }}
      .list-sel {{ fill: #e3f2fd; stroke: #1976d2; stroke-width: 1; }}
      .ux-banner {{ fill: #ede7f6; stroke: #7e57c2; stroke-width: 1; }}
      .ux-banner-t {{ fill: #4527a0; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 700; }}
      .ux-banner-b {{ fill: #4a148c; font-family: system-ui, sans-serif; font-size: 8px; }}
      .ux-ctx {{ fill: #1565c0; font-family: system-ui, sans-serif; font-size: 10px; font-weight: 600; }}
      .ux-sum {{ fill: #7e57c2; font-family: system-ui, sans-serif; font-size: 7px; }}
      .ux-snack {{ fill: #323232; }}
      .ux-snack-t {{ fill: #ffffff; font-family: system-ui, sans-serif; font-size: 11px; }}
      .ux-snack-s {{ fill: #b0bec5; font-family: system-ui, sans-serif; font-size: 8px; }}
      .row-excl {{ fill: #fce4ec; stroke: #f48fb1; stroke-width: 1; }}
      .cb-box {{ fill: #ffffff; stroke: #546e7a; stroke-width: 1.25; }}
      .cb-box-on {{ fill: #e3f2fd; stroke: #1976d2; stroke-width: 1.25; }}
      .cb-mark {{ fill: #0d47a1; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 700; text-anchor: middle; }}
      .grid-select-field {{ fill: #ffffff; stroke: #bdbdbd; stroke-width: 1; }}
      .grid-select-field.is-locked {{ fill: #f5f5f5; }}
      .grid-select-txt {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 11px; }}
      .grid-select-chev {{ fill: #757575; }}
      .pg-list-btn {{ fill: #ffffff; stroke: #90a4ae; stroke-width: 1; }}
      .pg-list-btn-t {{ fill: #37474f; font-family: system-ui, sans-serif; font-size: 9px; text-anchor: middle; }}
{_wireframe_authoring_styles()}
{wireframe_chrome_css()}
    </style>
  </defs>
  <g id="wf-main-layout-req02" class="wf-layout-main" data-wf-layer="main">
  <rect class="bg" width="1200" height="{zone_y}" id="shell-bg"/>
  <rect class="side" x="0" y="0" width="240" height="{zone_y}" id="shell-sidebar"/>
  <text x="16" y="340" class="navg" id="nav-group-admin">{L["admin"]}</text>
  <text x="24" y="366" class="nav" id="nav-users">{L["user_mgmt"]}</text>
  <text x="24" y="392" class="nav navhi" id="nav-groups">{L["perm_groups"]}</text>
  <rect class="bar" x="240" y="0" width="960" height="56" id="shell-appbar"/>
  <text x="260" y="36" class="bart" id="appbar-title">{L["app_title"]}</text>
  <rect class="paper" x="264" y="72" width="912" height="{CONTENT_PANEL_H}" rx="4" id="content-panel"/>
  <text x="288" y="108" class="view-title" id="view-title">{L["pg_title"]}</text>
  <rect class="paper" x="{LP_X}" y="{PY}" width="{LP_W}" height="{PANEL_H}" rx="4" id="group-list-panel"/>
  <text x="{LP_X + 10}" y="{PY + 20}" class="lbl" id="panel-list-title">{L["list_title"]}</text>
  <rect class="pg-list-btn" x="{LP_X + LP_W - 90}" y="{PY + 26}" width="36" height="20" rx="3" id="btn-group-add"/>
  <text x="{LP_X + LP_W - 72}" y="{PY + 39}" class="pg-list-btn-t" id="btn-group-add-label">{L["list_btn_add"]}</text>
  <rect class="pg-list-btn" x="{LP_X + LP_W - 48}" y="{PY + 26}" width="36" height="20" rx="3" id="btn-group-delete"/>
  <text x="{LP_X + LP_W - 30}" y="{PY + 39}" class="pg-list-btn-t" id="btn-group-delete-label">{L["list_btn_del"]}</text>
  <rect class="list-sel" x="{LP_X + 6}" y="{PY + 52}" width="{LP_W - 12}" height="28" rx="4" id="group-item-sel-bg"/>
  <rect x="{LP_X + 4}" y="{PY + 56}" width="3" height="20" rx="1" fill="#1976d2" id="group-item-accent"/>
  <text x="{LP_X + 14}" y="{PY + 70}" class="item" style="font-weight:600" id="group-item-1">{L["grp_ops"]}</text>
  <text x="{LP_X + 14}" y="{PY + 98}" class="item" id="group-item-2">{L["grp_dev"]}</text>
  <text x="{LP_X + 14}" y="{PY + 126}" class="item" id="group-item-3">ADMIN_EXT</text>
  <rect class="paper" x="{RP_X}" y="{PY}" width="{RP_W}" height="{PANEL_H}" rx="4" id="group-detail-panel"/>
  <rect class="group-strip" x="{TX}" y="{y_row(0)}" width="4" height="{RH * 2}" fill="#1976d2"/>
  <rect class="group-strip" x="{TX}" y="{y_row(2)}" width="4" height="{RH * 4}" fill="#7b1fa2"/>
  <rect class="group-strip" x="{TX}" y="{y_row(5)}" width="4" height="{RH}" fill="#388e3c"/>
  <rect class="group-strip" x="{TX}" y="{y_row(6)}" width="4" height="{RH * 3}" fill="#f57c00"/>
  <line class="vline" x1="{C1}" y1="{HY}" x2="{C1}" y2="{y_end}"/>
  <line class="vline" x1="{C2}" y1="{HY}" x2="{C2}" y2="{y_end}"/>
  <line class="vline" x1="{C3}" y1="{HY}" x2="{C3}" y2="{y_end}"/>
  <line class="vline" x1="{C4}" y1="{HY}" x2="{C4}" y2="{y_end}"/>
  <line class="vline" x1="{C5}" y1="{HY}" x2="{C5}" y2="{y_end}"/>
  <line class="vline" x1="{C6}" y1="{HY}" x2="{C6}" y2="{y_end}"/>
  <rect class="hdr-row" x="{TX}" y="{HY}" width="{TW}" height="28" id="matrix-hdr"/>
  <text x="{C0 + 6}" y="{HY + 18}" class="hdr">{L["hdr_cat"]}</text>
  <text x="{C1 + 6}" y="{HY + 18}" class="hdr">{L["hdr_screen"]}</text>
  <text x="{CX_ACC}" y="{HY + 12}" class="hdr-mid" font-size="8px">{L["hdr_access_1"]}</text>
  <text x="{CX_ACC}" y="{HY + 22}" class="hdr-mid" font-size="7px">{L["hdr_access_2"]}</text>
  <text x="{CX_SCOPE_HDR}" y="{HY + 18}" class="hdr-mid" font-size="8px">{L["hdr_scope"]}</text>
  <text x="{CX_WR}" y="{HY + 18}" class="hdr-mid">{L["hdr_write"]}</text>
  <text x="{CX_AP}" y="{HY + 18}" class="hdr-mid">{L["hdr_appr"]}</text>
  <text x="{CX_DEC}" y="{HY + 18}" class="hdr-mid">{L["hdr_decrypt"]}</text>
  <rect class="grid-frame" x="{TX}" y="{HY}" width="{TW}" height="{y_end - HY}" rx="2" id="grid-outline"/>
  <rect class="row-bg" x="{TX}" y="{y_row(0)}" width="{TW}" height="{RH}" id="row-pb"/>
  <text x="{C0 + 10}" y="{y_row(0) + 19}" class="item">{L["grp_log"]}</text>
  <text x="{C1 + 8}" y="{y_row(0) + 20}" class="item" id="matrix-screen-pb">{L["lbl_pb"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_ACC - CB_SZ // 2}" y="{cb_top(0)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-use-pb"/>
  <text x="{CX_ACC}" y="{cb_top(0) + 11}" class="cb-mark">\u2713</text>
  <text x="{C3 + 6}" y="{y_row(0) + 19}" class="tiny">{L["cell_na"]}</text>
  <text x="{CX_WR}" y="{y_row(0) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_AP}" y="{y_row(0) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_DEC - CB_SZ // 2}" y="{cb_top(0)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-dec-pb"/>
  <text x="{CX_DEC}" y="{cb_top(0) + 11}" class="cb-mark">\u2713</text>
  <rect class="row-bg-alt" x="{TX}" y="{y_row(1)}" width="{TW}" height="{RH}" id="row-java"/>
  <text x="{C0 + 10}" y="{y_row(1) + 19}" class="item">{L["grp_log"]}</text>
  <text x="{C1 + 8}" y="{y_row(1) + 20}" class="item" id="matrix-screen-java">{L["lbl_java"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_ACC - CB_SZ // 2}" y="{cb_top(1)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-use-java"/>
  <text x="{CX_ACC}" y="{cb_top(1) + 11}" class="cb-mark">\u2713</text>
  <text x="{C3 + 6}" y="{y_row(1) + 19}" class="tiny">{L["cell_na"]}</text>
  <text x="{CX_WR}" y="{y_row(1) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_AP}" y="{y_row(1) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_DEC - CB_SZ // 2}" y="{cb_top(1)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-dec-java"/>
  <text x="{CX_DEC}" y="{cb_top(1) + 11}" class="cb-mark">\u2713</text>
  <rect class="row-bg" x="{TX}" y="{y_row(2)}" width="{TW}" height="{RH}" id="row-act"/>
  <text x="{C0 + 10}" y="{y_row(2) + 19}" class="item">{L["grp_hist"]}</text>
  <text x="{C1 + 8}" y="{y_row(2) + 20}" class="item" id="matrix-screen-act">{L["lbl_act"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_ACC - CB_SZ // 2}" y="{cb_top(2)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-use-act"/>
  <text x="{CX_ACC}" y="{cb_top(2) + 11}" class="cb-mark">\u2713</text>
{sc_scope_act}
  <text x="{CX_WR}" y="{y_row(2) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_AP}" y="{y_row(2) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_DEC}" y="{y_row(2) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="row-bg-alt" x="{TX}" y="{y_row(3)}" width="{TW}" height="{RH}" id="row-sh"/>
  <text x="{C0 + 10}" y="{y_row(3) + 19}" class="item">{L["grp_hist"]}</text>
  <text x="{C1 + 8}" y="{y_row(3) + 20}" class="item" id="matrix-screen-sh">\uac80\uc0c9 \uc774\ub825</text>
  <rect class="cb-box cb-box-on" x="{CX_ACC - CB_SZ // 2}" y="{cb_top(3)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-use-sh"/>
  <text x="{CX_ACC}" y="{cb_top(3) + 11}" class="cb-mark">\u2713</text>
{sc_scope_sh}
  <text x="{CX_WR}" y="{y_row(3) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_AP - CB_SZ // 2}" y="{cb_top(3)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="appr-cb-sh"/>
  <text x="{CX_AP}" y="{cb_top(3) + 11}" class="cb-mark">\u2713</text>
  <text x="{CX_DEC}" y="{y_row(3) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="row-bg" x="{TX}" y="{y_row(4)}" width="{TW}" height="{RH}" id="row-pend"/>
  <text x="{C0 + 10}" y="{y_row(4) + 19}" class="item">{L["grp_hist"]}</text>
  <text x="{C1 + 8}" y="{y_row(4) + 20}" class="item" id="matrix-screen-pend">\ubcf5\ud638\ud654 \uc2b9\uc778 \uad00\ub9ac</text>
  <rect class="cb-box cb-box-on" x="{CX_ACC - CB_SZ // 2}" y="{cb_top(4)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-use-pend"/>
  <text x="{CX_ACC}" y="{cb_top(4) + 11}" class="cb-mark">\u2713</text>
{sc_scope_pend}
  <text x="{CX_WR}" y="{y_row(4) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_AP - CB_SZ // 2}" y="{cb_top(4)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="appr-cb-pend"/>
  <text x="{CX_AP}" y="{cb_top(4) + 11}" class="cb-mark">\u2713</text>
  <text x="{CX_DEC}" y="{y_row(4) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="row-bg-alt" x="{TX}" y="{y_row(5)}" width="{TW}" height="{RH}" id="row-stat"/>
  <text x="{C0 + 10}" y="{y_row(5) + 19}" class="item">{L["grp_stat"]}</text>
  <text x="{C1 + 8}" y="{y_row(5) + 20}" class="item" id="matrix-screen-stat">{L["lbl_stat"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_ACC - CB_SZ // 2}" y="{cb_top(5)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-use-stat"/>
  <text x="{CX_ACC}" y="{cb_top(5) + 11}" class="cb-mark">\u2713</text>
{sc_scope_stat}
  <text x="{CX_WR}" y="{y_row(5) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_AP}" y="{y_row(5) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_DEC}" y="{y_row(5) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="row-bg" x="{TX}" y="{y_row(6)}" width="{TW}" height="{RH}" id="row-um"/>
  <text x="{C0 + 10}" y="{y_row(6) + 19}" class="item">{L["admin"]}</text>
  <text x="{C1 + 8}" y="{y_row(6) + 20}" class="item" id="matrix-screen-um">\uc0ac\uc6a9\uc790 \uad00\ub9ac</text>
  <rect class="cb-box cb-box-on" x="{CX_ACC - CB_SZ // 2}" y="{cb_top(6)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-use-um"/>
  <text x="{CX_ACC}" y="{cb_top(6) + 11}" class="cb-mark">\u2713</text>
  <text x="{C3 + 6}" y="{y_row(6) + 19}" class="tiny">{L["cell_na"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_WR - CB_SZ // 2}" y="{cb_top(6)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-write-um"/>
  <text x="{CX_WR}" y="{cb_top(6) + 11}" class="cb-mark">\u2713</text>
  <text x="{CX_AP}" y="{y_row(6) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_DEC}" y="{y_row(6) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="row-bg-alt" x="{TX}" y="{y_row(7)}" width="{TW}" height="{RH}" id="row-pgm"/>
  <text x="{C0 + 10}" y="{y_row(7) + 19}" class="item">{L["admin"]}</text>
  <text x="{C1 + 8}" y="{y_row(7) + 20}" class="item" id="matrix-screen-pgm">\uad8c\ud55c \uadf8\ub8f9 \uad00\ub9ac</text>
  <rect class="cb-box cb-box-on" x="{CX_ACC - CB_SZ // 2}" y="{cb_top(7)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-use-pgm"/>
  <text x="{CX_ACC}" y="{cb_top(7) + 11}" class="cb-mark">\u2713</text>
  <text x="{C3 + 6}" y="{y_row(7) + 19}" class="tiny">{L["cell_na"]}</text>
  <rect class="cb-box cb-box-on" x="{CX_WR - CB_SZ // 2}" y="{cb_top(7)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-write-pgm"/>
  <text x="{CX_WR}" y="{cb_top(7) + 11}" class="cb-mark">\u2713</text>
  <text x="{CX_AP}" y="{y_row(7) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_DEC}" y="{y_row(7) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="row-excl" x="{TX}" y="{y_row(8)}" width="{TW}" height="{RH}" id="row-dept-excluded"/>
  <text x="{C0 + 10}" y="{y_row(8) + 19}" class="item">{L["admin"]}</text>
  <text x="{C1 + 8}" y="{y_row(8) + 20}" class="item" id="matrix-screen-dept">{L["lbl_dept_appr"]}</text>
  <rect class="cb-box" x="{CX_ACC - CB_SZ // 2}" y="{cb_top(8)}" width="{CB_SZ}" height="{CB_SZ}" rx="2" id="cb-use-off-demo"/>
  <text x="{C3 + 6}" y="{y_row(8) + 19}" class="tiny">{L["cell_na"]}</text>
  <text x="{CX_WR}" y="{y_row(8) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_AP}" y="{y_row(8) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <text x="{CX_DEC}" y="{y_row(8) + 19}" class="tiny-mid">{L["cell_na"]}</text>
  <rect class="ux-snack" x="688" y="{SNACK_Y}" width="268" height="40" rx="6" id="ux-snackbar-mock"/>
{wf_mock_ui_tag(696, SNACK_Y + 2, "req02-snack")}
  <text x="704" y="{SNACK_Y + 20}" class="ux-snack-t" id="ux-snack-msg">{L["ux_snack"]}</text>
  <text x="704" y="{SNACK_Y + 32}" class="ux-snack-s" id="ux-snack-hint">{L["ux_snack_sub"]}</text>
  <rect x="1056" y="{SAVE_Y}" width="120" height="36" rx="4" fill="#1976d2" id="btn-save"/>
  <text x="1116" y="{SAVE_Y + 22}" text-anchor="middle" fill="#ffffff" font-family="system-ui, sans-serif" font-size="13px" id="btn-save-label">{L["save"]}</text>
  </g>
{wire_stack_req02}
</svg>
"""


def svg3() -> str:
    """TC-09 wire: compact tree mock in white card; all wf-chrome-annotations stacked below."""
    CONTENT_TOP = 80
    CONTENT_BOTTOM = 404
    CONTENT_H = CONTENT_BOTTOM - CONTENT_TOP
    zone_y, ANNO_Y0 = wf_annotation_zone_layout(CONTENT_BOTTOM)
    e = lambda t: escape(t, entities={'"': "&quot;", "&": "&amp;"})

    y = ANNO_Y0
    d3: list[str] = []
    d3.append(
        f'  <text x="288" y="{y + 11}" class="wf-scope-legend-line wf-chrome-annotations" font-size="6.5px" '
        f'id="fe-banner-wire-line">{e(L["fe_banner_wire_note"])}</text>'
    )
    y += 14
    d3.append(
        f'  <text x="288" y="{y + 11}" class="banner-title wf-chrome-annotations" id="fe-warn-title">'
        f'{e(L["fe_warn_title"])}</text>'
    )
    y += 16
    d3.append(
        f'  <text x="288" y="{y + 11}" class="banner-body wf-chrome-annotations" id="fe-warn-l1">'
        f'{e(L["fe_warn_l1"])}</text>'
    )
    y += 14
    d3.append(
        f'  <text x="288" y="{y + 11}" class="banner-body wf-chrome-annotations" id="fe-warn-l2">'
        f'{e(L["fe_warn_l2"])}</text>'
    )
    y += 14
    d3.append(
        f'  <text x="288" y="{y + 11}" class="banner-body wf-chrome-annotations" id="fe-warn-l3">'
        f'{e(L["fe_warn_l3"])}</text>'
    )
    y += 16
    d3.append(wf_section_divider(y, "req03-s1"))
    y += 10
    d3.append(
        f'  <text x="288" y="{y + 11}" class="view-title wf-anno-text wf-chrome-annotations" id="view-title">'
        f'{e(L["uh_title"])}</text>'
    )
    y += 22
    for i, ln in enumerate(_wrap_text_lines(L["uh_hint"], 88)):
        hid = "view-hint" if i == 0 else f"view-hint-l{i + 1}"
        d3.append(
            f'  <text x="288" y="{y + 11}" class="hint wf-anno-text wf-chrome-annotations" id="{hid}">{e(ln)}</text>'
        )
        y += 14
    y += 4
    for i, ln in enumerate(_wrap_text_lines(L["uh_impl"], 88)):
        iid = "uh-impl" if i == 0 else f"uh-impl-l{i + 1}"
        d3.append(
            f'  <text x="288" y="{y + 11}" class="meta wf-anno-text wf-chrome-annotations" id="{iid}">{e(ln)}</text>'
        )
        y += 13
    y += 2
    for i, ln in enumerate(_wrap_text_lines(L["uh_layout_note"], 88)):
        lid = "uh-layout" if i == 0 else f"uh-layout-l{i + 1}"
        d3.append(
            f'  <text x="288" y="{y + 11}" class="meta wf-anno-text wf-chrome-annotations" id="{lid}">{e(ln)}</text>'
        )
        y += 13
    y += 6
    d3.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{y + 11}" class="meta wf-anno-text wf-chrome-annotations" id="tree-sec-label">'
        f'{e(L["tree_title"])}</text>'
    )
    y += 18
    d3.append(
        f'  <text x="{WIRE_ANNO_TEXT_X}" y="{y + 11}" class="meta wf-anno-text wf-chrome-annotations" id="no-right-panel">'
        f"\u2192 \uc624\ub978\ucabd \ubcc4\ub3c4 \uc0c1\uc138 \ud328\ub110 \uc5c6\uc74c (UserManagement \ub2e8\uc77c \ub808\uc774\uc544\uc6c3)</text>"
    )
    y += 12
    d3.append(wf_section_divider(y, "req03-s2"))
    y += 10
    d3.append(
        f'  <text x="288" y="{y + 11}" class="warn-t wf-chrome-annotations" id="rm-box-heading">'
        f'{e(L["rm_box_t"])}</text>'
    )
    y += 18
    d3.append(
        f'  <text x="288" y="{y + 11}" class="node wf-chrome-annotations" id="rm-box-body">{e(L["rm_box_b"])}</text>'
    )
    y += 18
    d3.append(
        f'  <text x="288" y="{y + 11}" class="node wf-chrome-annotations" id="tc9-line">{e(L["tc9"])}</text>'
    )
    y += 18
    d3.append(
        f'  <text x="288" y="{y + 11}" class="meta wf-anno-text wf-chrome-annotations" id="tc09-api-foot">'
        f'{e(L["tc09_api_foot"])}</text>'
    )
    y += 20
    META_SEP_Y = y
    META_Y = META_SEP_Y + 10
    VIEW_H = META_Y + wireframe_visible_meta_panel_height() + 24
    svg3_annotation_dock = "\n".join(d3)
    wire_stack_req03 = wire_annotations_stack(
        "req03",
        f"{svg3_annotation_dock}\n{wireframe_visible_meta_panel(WIRE_ANNO_TEXT_X, META_Y, 1104, 70, 'req03', sep_y=META_SEP_Y)}",
        zone_y=zone_y,
        view_h=VIEW_H,
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 {VIEW_H}" role="img" aria-label="TC-09 hierarchy API vs UserManagement route — no separate screen" data-req="20260323-approver-eligibility" data-tc="TC-09" data-wireframe-semantics="v1" data-wireframe-rules-ref=".cursor/rules/svg-wireframe-semantics.mdc" data-wireframe-visible-meta="wireframe-visible-meta-req03" data-wireframe-chrome="full" data-ref="frontend/src/components/UserManagement/UserManagement.js" data-frontend-note="App.js renders UserManagement for both views" data-col-screen-name-policy="na-tc09-hierarchy-wireframe">
  <defs>
    <style>
      .view-title {{ fill: #212121; font-family: "Noto Sans KR", "Roboto", system-ui, sans-serif; font-size: 22px; font-weight: 600; }}
      .bg {{ fill: #f5f5f5; }}
      .side {{ fill: #ffffff; stroke: #e0e0e0; stroke-width: 1; }}
      .bar {{ fill: #1976d2; }}
      .bart {{ fill: #ffffff; font-family: system-ui, sans-serif; font-size: 14px; }}
      .navg {{ fill: #616161; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
      .nav {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 12px; }}
      .navhi {{ fill: #1976d2; font-weight: 600; }}
      .paper {{ fill: #ffffff; stroke: #e0e0e0; stroke-width: 1; }}
      .hint {{ fill: #555555; font-family: system-ui, sans-serif; font-size: 11px; }}
      .meta {{ fill: #616161; font-family: system-ui, sans-serif; font-size: 10px; }}
      .banner {{ fill: #fff3e0; stroke: #e65100; stroke-width: 2; }}
      .banner-title {{ fill: #e65100; font-family: system-ui, sans-serif; font-size: 12px; font-weight: 700; }}
      .banner-body {{ fill: #5d4037; font-family: system-ui, sans-serif; font-size: 10px; }}
      .tree-box {{ fill: #fafafa; stroke: #dddddd; stroke-width: 1; }}
      .node-strong {{ fill: #1976d2; font-family: system-ui, sans-serif; font-size: 12px; font-weight: 600; }}
      .node {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 12px; }}
      .hdr-bg {{ fill: #f5f5f5; stroke: #dddddd; stroke-width: 1; }}
      .hdr {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
      .row {{ fill: rgba(255, 255, 255, 0); stroke: #e3e8ef; stroke-width: 1; }}
      .warn {{ fill: #ffebee; stroke: #e57373; stroke-width: 1; }}
      .warn-t {{ fill: #c62828; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
{_wireframe_authoring_styles()}
{wireframe_chrome_css()}
    </style>
  </defs>
  <g id="wf-main-layout-req03" class="wf-layout-main" data-wf-layer="main">
  <rect class="bg" width="1200" height="{zone_y}" id="shell-bg"/>
  <rect class="side" x="0" y="0" width="240" height="{zone_y}" id="shell-sidebar"/>
  <text x="16" y="340" class="navg" id="nav-group-admin">{L["admin"]}</text>
  <text x="24" y="366" class="nav navhi" id="nav-users">{L["user_mgmt"]}</text>
  <text x="24" y="392" class="nav" id="nav-groups">{L["perm_groups"]}</text>
  <rect class="bar" x="240" y="0" width="960" height="56" id="shell-appbar"/>
  <text x="260" y="36" class="bart" id="appbar-title">{L["app_title"]}</text>
  <rect class="paper" x="264" y="80" width="912" height="{CONTENT_H}" rx="4" id="content-panel"/>
  <text x="288" y="108" class="view-title" id="product-route-label">{L["user_mgmt"]}</text>
  <rect class="tree-box" x="288" y="124" width="864" height="280" rx="4" id="tree-section"/>
  <text x="304" y="176" class="node-strong" id="dept-node-ops">{L["ops_dept"]}</text>
  <rect class="hdr-bg" x="338" y="192" width="790" height="28" id="mini-hdr"/>
  <text x="350" y="210" class="hdr">{L["col_name"]}</text>
  <text x="450" y="210" class="hdr">{L["col_uid"]}</text>
  <text x="820" y="210" class="hdr">{L["col_group"]}</text>
  <rect class="row" x="338" y="220" width="790" height="32" id="mini-row"/>
  <text x="350" y="240" class="node">{L["hong"]}</text>
  <text x="450" y="240" class="node">20260001</text>
  <text x="820" y="240" class="node">{L["grp_ops"]}</text>
  </g>
{wire_stack_req03}
</svg>
"""


def svg4() -> str:
    """Test-state cards = main body; document title/sub/tags only below cards."""
    MAIN_CONTENT_BOTTOM = 736 + 140
    zone_y, anno_y0 = wf_annotation_zone_layout(MAIN_CONTENT_BOTTOM)
    TITLE_Y = anno_y0 + 8
    SUB_Y = anno_y0 + 28
    META_SEP_Y = SUB_Y + 20
    META_Y = META_SEP_Y + 10
    VIEW_H = META_Y + wireframe_visible_meta_panel_height() + 24
    ax4 = WIRE_ANNO_TEXT_X_SVG4
    wire_body_req04 = "\n".join(
        [
            f'  <text x="{ax4}" y="{TITLE_Y}" class="title wf-anno-text wf-chrome-annotations" id="main-title" text-anchor="start">{L["ts_main"]}</text>',
            f'  <text x="{ax4}" y="{SUB_Y}" class="sub wf-anno-text wf-chrome-annotations" id="main-sub" text-anchor="start">{L["ts_sub"]}</text>',
            wf_section_divider(META_SEP_Y, "req04-s-meta", x1=ax4, x2=1160),
            wireframe_visible_meta_panel(
                ax4,
                META_Y,
                1136,
                70,
                "req04",
                sep_y=None,
                sep_x1=ax4,
                sep_x2=1160,
            ),
        ]
    )
    wire_stack_req04 = wire_annotations_stack(
        "req04",
        wire_body_req04,
        zone_y=zone_y,
        view_h=VIEW_H,
        anno_text_x=ax4,
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 {VIEW_H}" role="img" aria-label="Decrypt approval UI API test states" data-req="20260323-approver-eligibility" data-wireframe-semantics="v1" data-wireframe-rules-ref=".cursor/rules/svg-wireframe-semantics.mdc" data-wireframe-visible-meta="wireframe-visible-meta-req04" data-wireframe-chrome="full" data-ref="frontend/src/components/PendingApprovals/PendingApprovals.js" data-col-screen-name-policy="na-api-test-cards">
  <defs>
    <style>
      .title {{ fill: #212121; font-family: "Noto Sans KR", "Roboto", system-ui, sans-serif; font-size: 22px; font-weight: 600; }}
      .sub {{ fill: #616161; font-family: system-ui, sans-serif; font-size: 12px; }}
      .bg {{ fill: #eceff1; }}
      .card {{ fill: #ffffff; stroke: #cfd8dc; stroke-width: 1; }}
      .card-title {{ fill: #37474f; font-family: system-ui, sans-serif; font-size: 13px; font-weight: 600; }}
      .body {{ fill: #424242; font-family: system-ui, sans-serif; font-size: 11px; }}
      .code {{ fill: #1565c0; font-family: ui-monospace, monospace; font-size: 10px; }}
      .ok {{ fill: #2e7d32; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
      .deny {{ fill: #c62828; font-family: system-ui, sans-serif; font-size: 11px; font-weight: 600; }}
{_wireframe_authoring_styles()}
{wireframe_chrome_css()}
    </style>
  </defs>
  <g id="wf-main-layout-req04" class="wf-layout-main" data-wf-layer="main">
  <rect class="bg" width="1200" height="{zone_y}" id="canvas-bg"/>
  <rect class="card wf-anno-surf" x="32" y="92" width="540" height="200" rx="6" id="card-a"/>
{wf_anno_tag(44, 102, "req04-a")}
  <text x="48" y="120" class="card-title" id="card-a-title">{L["a_t"]}</text>
  <text x="48" y="142" class="body">{L["a_b1"]}</text>
  <text x="48" y="162" class="ok">{L["a_ok"]}</text>
  <text x="48" y="182" class="code">{L["a_code"]}</text>
  <text x="48" y="202" class="body">{L["a_b2"]}</text>
  <text x="48" y="224" class="sub">{L["a_man"]}</text>
  <rect class="card wf-anno-surf" x="588" y="92" width="580" height="200" rx="6" id="card-b"/>
{wf_anno_tag(600, 102, "req04-b")}
  <text x="604" y="120" class="card-title" id="card-b-title">{L["b_t"]}</text>
  <text x="604" y="142" class="body">{L["b_b1"]}</text>
  <text x="604" y="162" class="deny">{L["b_dn"]}</text>
  <text x="604" y="182" class="body">{L["b_b2"]}</text>
  <text x="604" y="202" class="code">{L["b_code"]}</text>
  <rect class="card wf-anno-surf" x="32" y="308" width="540" height="200" rx="6" id="card-c"/>
{wf_anno_tag(44, 318, "req04-c")}
  <text x="48" y="336" class="card-title" id="card-c-title">{L["c_t"]}</text>
  <text x="48" y="358" class="body">{L["c_b1"]}</text>
  <text x="48" y="378" class="deny">{L["c_dn"]}</text>
  <text x="48" y="398" class="body">{L["c_b2"]}</text>
  <text x="48" y="420" class="code">{L["c_code"]}</text>
  <rect class="card wf-anno-surf" x="588" y="308" width="580" height="200" rx="6" id="card-d"/>
{wf_anno_tag(600, 318, "req04-d")}
  <text x="604" y="336" class="card-title" id="card-d-title">{L["d_t"]}</text>
  <text x="604" y="358" class="body">{L["d_b1"]}</text>
  <text x="604" y="378" class="deny">{L["d_dn"]}</text>
  <text x="604" y="398" class="body">{L["d_b2"]}</text>
  <text x="604" y="420" class="code">{L["d_code"]}</text>
  <rect class="card wf-anno-surf" x="32" y="524" width="1136" height="200" rx="6" id="card-e"/>
{wf_anno_tag(44, 534, "req04-e")}
  <text x="48" y="552" class="card-title" id="card-e-title">{L["e_t"]}</text>
  <text x="48" y="574" class="body">{L["e_b1"]}</text>
  <text x="48" y="594" class="deny">{L["e_dn"]}</text>
  <text x="48" y="614" class="body">{L["e_b2"]}</text>
  <text x="48" y="636" class="sub">{L["e_aud"]}</text>
  <rect class="card wf-anno-surf" x="32" y="736" width="1136" height="140" rx="6" id="card-f"/>
{wf_anno_tag(44, 746, "req04-f")}
  <text x="48" y="764" class="card-title" id="card-f-title">{L["f_t"]}</text>
  <text x="48" y="786" class="body">{L["f_b1"]}</text>
  <text x="48" y="806" class="body">{L["f_b2"]}</text>
  <text x="48" y="826" class="body">{L["f_b3"]}</text>
  </g>
{wire_stack_req04}
</svg>
"""


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    files = [
        ("req-20260323-01-user-management-final.svg", svg1()),
        ("req-20260323-02-permission-group-matrix-final.svg", svg2()),
        ("req-20260323-03-user-permission-hierarchy-final.svg", svg3()),
        ("req-20260323-04-decrypt-approval-ui-test-states.svg", svg4()),
    ]
    for name, body in files:
        (OUT / name).write_text(body, encoding="utf-8", newline="\n")
    print("Wrote", len(files), "SVG files to", OUT)


if __name__ == "__main__":
    main()
