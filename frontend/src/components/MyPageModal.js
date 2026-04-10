import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { fetchAuthLoginMode } from '../services/authConfigService';
import { fetchAuthMe, postOwnPassword } from '../services/myPageService';
import { getErrorMessage } from '../utils/errorMessage';

const titleId = 'my-page-modal-title';

/**
 * My page: read-only profile from GET /api/auth/me; password change in local mode only.
 */
function MyPageModal({ open, onClose }) {
  const [loadError, setLoadError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loginMode, setLoginMode] = useState(null);
  const [department, setDepartment] = useState('');
  const [displayName, setDisplayName] = useState('');

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [formError, setFormError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const resetPasswordFields = useCallback(() => {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setFormError('');
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    let cancelled = false;
    (async () => {
      setLoadError(null);
      setLoading(true);
      setSuccessMessage('');
      resetPasswordFields();
      try {
        const [mode, meRes] = await Promise.all([fetchAuthLoginMode(), fetchAuthMe()]);
        if (cancelled) return;
        setLoginMode(mode);
        const u = meRes?.data?.user;
        const sc = u?.selfContext;
        setDepartment(sc?.department != null && String(sc.department).trim() !== '' ? String(sc.department) : '—');
        setDisplayName(
          sc?.username != null && String(sc.username).trim() !== ''
            ? String(sc.username)
            : u?.username != null
              ? String(u.username)
              : '—'
        );
      } catch (e) {
        if (!cancelled) {
          setLoadError(getErrorMessage(e, '사용자 정보를 불러오지 못했습니다.'));
          setLoginMode(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, resetPasswordFields]);

  const handleClose = () => {
    if (submitting) return;
    resetPasswordFields();
    setSuccessMessage('');
    setLoadError(null);
    onClose();
  };

  const validateClient = () => {
    const cur = String(currentPassword ?? '');
    const nwly = String(newPassword ?? '');
    const conf = String(confirmPassword ?? '');
    if (!cur.trim()) return '현재 비밀번호를 입력해 주세요.';
    if (!nwly.trim()) return '새 비밀번호를 입력해 주세요.';
    if (nwly !== conf) return '새 비밀번호와 확인이 일치하지 않습니다.';
    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSuccessMessage('');
    const v = validateClient();
    if (v) {
      setFormError(v);
      return;
    }
    setFormError('');
    setSubmitting(true);
    try {
      await postOwnPassword({
        currentPassword: currentPassword.trim(),
        newPassword: newPassword.trim(),
        confirmNewPassword: confirmPassword.trim(),
      });
      setSuccessMessage('비밀번호가 변경되었습니다.');
      resetPasswordFields();
    } catch (err) {
      setFormError(getErrorMessage(err, '비밀번호 변경에 실패했습니다.'));
    } finally {
      setSubmitting(false);
    }
  };

  const showPasswordSection = loginMode === 'local';

  return (
    <Dialog
      open={open}
      onClose={(event, reason) => {
        if (reason === 'backdropClick' || reason === 'escapeKeyDown') handleClose();
      }}
      maxWidth="sm"
      fullWidth
      aria-labelledby={titleId}
    >
      <DialogTitle id={titleId}>마이페이지</DialogTitle>
      <DialogContent>
        {loading && (
          <Box display="flex" justifyContent="center" py={2} role="status" aria-live="polite">
            <CircularProgress size={32} aria-label="불러오는 중" />
          </Box>
        )}
        {!loading && loadError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {loadError}
          </Alert>
        )}
        {!loading && !loadError && (
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography component="h3" variant="subtitle2" sx={{ fontWeight: 600 }}>
              프로필
            </Typography>
            <TextField label="부서" value={department} disabled fullWidth margin="dense" />
            <TextField label="이름(표시명)" value={displayName} disabled fullWidth margin="dense" />

            {loginMode === 'ad' && (
              <Alert severity="info">
                디렉터리(AD) 로그인 환경에서는 애플리케이션 비밀번호를 여기서 변경할 수 없습니다. 비밀번호는 조직에서 관리됩니다.
              </Alert>
            )}

            {showPasswordSection && (
              <>
                <Typography component="h3" variant="subtitle2" sx={{ fontWeight: 600, pt: 1 }}>
                  비밀번호 변경
                </Typography>
                {successMessage ? (
                  <Alert severity="success" role="status">
                    {successMessage}
                  </Alert>
                ) : null}
                {formError ? (
                  <Alert severity="error" role="alert">
                    {formError}
                  </Alert>
                ) : null}
                <Box component="form" id="my-page-password-form" onSubmit={handleSubmit}>
                  <Stack spacing={2}>
                    <TextField
                      label="현재 비밀번호"
                      type="password"
                      name="currentPassword"
                      autoComplete="current-password"
                      value={currentPassword}
                      onChange={(ev) => setCurrentPassword(ev.target.value)}
                      disabled={submitting}
                      fullWidth
                      margin="dense"
                    />
                    <TextField
                      label="새 비밀번호"
                      type="password"
                      name="newPassword"
                      autoComplete="new-password"
                      value={newPassword}
                      onChange={(ev) => setNewPassword(ev.target.value)}
                      disabled={submitting}
                      fullWidth
                      margin="dense"
                    />
                    <TextField
                      label="새 비밀번호 확인"
                      type="password"
                      name="confirmPassword"
                      autoComplete="new-password"
                      value={confirmPassword}
                      onChange={(ev) => setConfirmPassword(ev.target.value)}
                      disabled={submitting}
                      fullWidth
                      margin="dense"
                    />
                  </Stack>
                </Box>
              </>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button type="button" onClick={handleClose} disabled={submitting}>
          닫기
        </Button>
        {showPasswordSection && !loading && !loadError ? (
          <Button
            type="submit"
            form="my-page-password-form"
            variant="contained"
            color="primary"
            disabled={submitting}
          >
            비밀번호 변경
          </Button>
        ) : null}
      </DialogActions>
    </Dialog>
  );
}

export default MyPageModal;
