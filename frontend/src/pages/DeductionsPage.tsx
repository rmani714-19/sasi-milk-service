import { useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { money, today } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { ProducerPicker } from '../components/ProducerPicker';
import { useToast } from '../components/ui';
import type { Producer } from '../api/types';

interface DType { id: number; code: string; name: string }
interface DTxn { id: number; deductionTypeId: number; amount: number; txnDate: string; description?: string; status: string }

export function DeductionsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const [producer, setProducer] = useState<Producer | null>(null);
  const [f, setF] = useState({ typeCode: 'FEED', amount: '', txnDate: today(), description: '' });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  const types = useQuery({ queryKey: ['deduction-types'], queryFn: async () => (await api.get<DType[]>('/deductions/types')).data });
  const list = useQuery({
    queryKey: ['deductions', producer?.id],
    queryFn: async () => (await api.get<DTxn[]>('/deductions', { params: { producerId: producer!.id } })).data,
    enabled: !!producer,
  });
  const create = useMutation({
    mutationFn: async () =>
      (await api.post('/deductions', {
        producerId: producer!.id,
        typeCode: f.typeCode,
        amount: Number(f.amount),
        txnDate: f.txnDate,
        description: f.description || undefined,
      })).data,
    onSuccess: () => {
      toast.success('Deduction added');
      setF({ ...f, amount: '', description: '' });
      qc.invalidateQueries({ queryKey: ['deductions'] });
    },
    onError: toast.fromError,
  });

  const typeName = (id: number) => types.data?.find((t) => t.id === id)?.code ?? id;

  return (
    <>
      <PageHeader title="Deductions" subtitle="Fixed amounts — cow feed, emergency cash, etc." />
      <Stack spacing={3}>
        <ProducerPicker value={producer} onChange={setProducer} />
        {producer && (
          <>
            <Card>
              <CardContent>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                  <TextField select label="Type" value={f.typeCode} onChange={set('typeCode')} sx={{ minWidth: 160 }}>
                    {types.data?.map((t) => (
                      <MenuItem key={t.id} value={t.code}>
                        {t.name}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField label="Amount" type="number" value={f.amount} onChange={set('amount')} />
                  <TextField label="Date" type="date" value={f.txnDate} onChange={set('txnDate')} InputLabelProps={{ shrink: true }} />
                  <TextField label="Note" value={f.description} onChange={set('description')} />
                  <Button variant="contained" disabled={!Number(f.amount) || create.isPending} onClick={() => create.mutate()}>
                    Add
                  </Button>
                </Stack>
              </CardContent>
            </Card>
            <Card>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell align="right">Amount</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Note</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {list.data?.map((d) => (
                    <TableRow key={d.id}>
                      <TableCell>{d.txnDate}</TableCell>
                      <TableCell>{typeName(d.deductionTypeId)}</TableCell>
                      <TableCell align="right">{money(d.amount)}</TableCell>
                      <TableCell>
                        <Chip size="small" label={d.status} variant="outlined" />
                      </TableCell>
                      <TableCell>{d.description ?? ''}</TableCell>
                    </TableRow>
                  ))}
                  {list.data?.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} sx={{ textAlign: 'center', py: 3, color: 'text.secondary' }}>
                        No deductions
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </Card>
          </>
        )}
      </Stack>
    </>
  );
}
