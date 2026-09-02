import { useEffect, useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid2';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import SmsIcon from '@mui/icons-material/Sms';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';
import EmailIcon from '@mui/icons-material/Email';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';
import { QueryBoundary, SectionTitle, useToast } from '../components/ui';
import type { NotificationChannel, NotificationLogItem, NotificationTemplateItem } from '../api/types';

const CHANNEL_META: Record<string, { label: string; icon: JSX.Element; hint: string }> = {
  SMS: { label: 'SMS', icon: <SmsIcon />, hint: 'Requires an India DLT-registered sender' },
  WHATSAPP: { label: 'WhatsApp', icon: <WhatsAppIcon />, hint: 'Requires a WhatsApp Business (BSP) account' },
  EMAIL: { label: 'Email', icon: <EmailIcon />, hint: 'Requires an email/SMTP or SES sender' },
};

export function NotificationsPage() {
  const toast = useToast();
  const qc = useQueryClient();

  const channels = useQuery({
    queryKey: ['notif', 'channels'],
    queryFn: async () => (await api.get<NotificationChannel[]>('/notifications/channels')).data,
  });
  const templates = useQuery({
    queryKey: ['notif', 'templates'],
    queryFn: async () => (await api.get<NotificationTemplateItem[]>('/notifications/templates')).data,
  });
  const recent = useQuery({
    queryKey: ['notif', 'recent'],
    queryFn: async () => (await api.get<NotificationLogItem[]>('/notifications')).data,
  });

  const saveChannel = useMutation({
    mutationFn: async (c: NotificationChannel) =>
      (await api.put(`/notifications/channels/${c.channel}`, {
        enabled: c.enabled,
        provider: c.provider || undefined,
        senderId: c.senderId || undefined,
      })).data,
    onSuccess: () => {
      toast.success('Channel saved');
      qc.invalidateQueries({ queryKey: ['notif', 'channels'] });
    },
    onError: toast.fromError,
  });

  const saveTemplate = useMutation({
    mutationFn: async (t: NotificationTemplateItem) =>
      (await api.put(`/notifications/templates/${t.id}`, {
        subject: t.subject || undefined,
        body: t.body,
        providerTemplateId: t.providerTemplateId || undefined,
        status: t.status,
      })).data,
    onSuccess: () => {
      toast.success('Template saved');
      qc.invalidateQueries({ queryKey: ['notif', 'templates'] });
      setEditTpl(null);
    },
    onError: toast.fromError,
  });

  const [editTpl, setEditTpl] = useState<NotificationTemplateItem | null>(null);

  return (
    <>
      <PageHeader title="Notifications" subtitle="Enable the channels you use, then tune the message templates" />

      <SectionTitle>Channels</SectionTitle>
      <QueryBoundary query={channels}>
        {(list) => (
          <Grid container spacing={2} sx={{ mb: 4 }}>
            {['SMS', 'WHATSAPP', 'EMAIL'].map((code) => {
              const c = list.find((x) => x.channel === code) ?? { channel: code, enabled: false };
              return <ChannelCard key={code} value={c} busy={saveChannel.isPending} onSave={saveChannel.mutate} />;
            })}
          </Grid>
        )}
      </QueryBoundary>

      <SectionTitle sx={{ mt: 4 }}>Message templates</SectionTitle>
      <Card sx={{ mb: 4, overflowX: 'auto' }}>
        <QueryBoundary query={templates} empty={(d) => d.length === 0}>
          {(list) => (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Event</TableCell>
                  <TableCell>Channel</TableCell>
                  <TableCell>Message</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {list.map((t) => (
                  <TableRow key={t.id}>
                    <TableCell>{t.code}</TableCell>
                    <TableCell>{t.channel}</TableCell>
                    <TableCell sx={{ maxWidth: 380, whiteSpace: 'normal' }}>
                      <Typography variant="caption">{t.body}</Typography>
                    </TableCell>
                    <TableCell>
                      <StatusChip status={t.status} />
                    </TableCell>
                    <TableCell align="right">
                      <Button size="small" onClick={() => setEditTpl(t)}>
                        Edit
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </QueryBoundary>
      </Card>

      <SectionTitle>Recent</SectionTitle>
      <Card sx={{ overflowX: 'auto' }}>
        <QueryBoundary query={recent} empty={(d) => d.length === 0}>
          {(list) => (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Event</TableCell>
                  <TableCell>Channel</TableCell>
                  <TableCell>Recipient</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Message</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {list.slice(0, 40).map((n) => (
                  <TableRow key={n.id}>
                    <TableCell>{n.eventType}</TableCell>
                    <TableCell>{n.channel}</TableCell>
                    <TableCell>{n.recipient}</TableCell>
                    <TableCell>
                      <StatusChip status={n.status} />
                    </TableCell>
                    <TableCell sx={{ maxWidth: 320, whiteSpace: 'normal' }}>
                      <Typography variant="caption">{n.renderedBody}</Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </QueryBoundary>
      </Card>

      <TemplateDialog
        key={editTpl?.id ?? 'none'}
        template={editTpl}
        busy={saveTemplate.isPending}
        onClose={() => setEditTpl(null)}
        onSave={saveTemplate.mutate}
      />
    </>
  );
}

function ChannelCard({
  value,
  busy,
  onSave,
}: {
  value: NotificationChannel;
  busy: boolean;
  onSave: (c: NotificationChannel) => void;
}) {
  const [c, setC] = useState<NotificationChannel>(value);
  useEffect(() => setC(value), [value]);
  const meta = CHANNEL_META[c.channel];
  const dirty =
    c.enabled !== value.enabled || (c.provider ?? '') !== (value.provider ?? '') || (c.senderId ?? '') !== (value.senderId ?? '');

  return (
    <Grid size={{ xs: 12, md: 4 }}>
      <Card sx={{ height: '100%' }}>
        <CardContent>
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Stack direction="row" spacing={1} alignItems="center">
              {meta.icon}
              <Typography variant="h6">{meta.label}</Typography>
            </Stack>
            <FormControlLabel
              control={<Switch checked={c.enabled} onChange={(e) => setC({ ...c, enabled: e.target.checked })} />}
              label={c.enabled ? 'Enabled' : 'Disabled'}
              labelPlacement="start"
            />
          </Stack>
          <Typography variant="caption" color="text.secondary">
            {meta.hint}
          </Typography>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <TextField label="Provider" value={c.provider ?? ''} onChange={(e) => setC({ ...c, provider: e.target.value })} />
            <TextField label="Sender ID" value={c.senderId ?? ''} onChange={(e) => setC({ ...c, senderId: e.target.value })} />
            <Button variant="contained" disabled={busy || !dirty} onClick={() => onSave(c)}>
              Save
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Grid>
  );
}

function TemplateDialog({
  template,
  busy,
  onClose,
  onSave,
}: {
  template: NotificationTemplateItem | null;
  busy: boolean;
  onClose: () => void;
  onSave: (t: NotificationTemplateItem) => void;
}) {
  const [t, setT] = useState<NotificationTemplateItem | null>(template);
  useEffect(() => setT(template), [template]);

  return (
    <Dialog open={!!template} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        {template?.code} · {template?.channel}
      </DialogTitle>
      <DialogContent>
        {t && (
          <Stack spacing={2} sx={{ mt: 1 }}>
            {t.channel === 'EMAIL' && (
              <TextField label="Subject" value={t.subject ?? ''} onChange={(e) => setT({ ...t, subject: e.target.value })} />
            )}
            <TextField
              label="Message body"
              value={t.body}
              onChange={(e) => setT({ ...t, body: e.target.value })}
              multiline
              minRows={4}
              helperText="Use {{placeholders}} — e.g. {{producerName}}, {{litres}}, {{ratePerLitre}}, {{amount}}"
            />
            <TextField
              label="Provider template id (DLT / WhatsApp)"
              value={t.providerTemplateId ?? ''}
              onChange={(e) => setT({ ...t, providerTemplateId: e.target.value })}
            />
            <FormControlLabel
              control={
                <Switch
                  checked={t.status === 'ACTIVE'}
                  onChange={(e) => setT({ ...t, status: e.target.checked ? 'ACTIVE' : 'INACTIVE' })}
                />
              }
              label={t.status === 'ACTIVE' ? 'Active' : 'Inactive'}
            />
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={busy || !t?.body.trim()} onClick={() => t && onSave(t)}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
