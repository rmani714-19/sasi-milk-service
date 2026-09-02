import { useState } from 'react';
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { Page, Producer } from '../api/types';

export function ProducerPicker({
  value,
  onChange,
  label = 'Farmer',
}: {
  value: Producer | null;
  onChange: (p: Producer | null) => void;
  label?: string;
}) {
  const [input, setInput] = useState('');
  const list = useQuery({
    queryKey: ['producers', 'picker', input],
    queryFn: async () =>
      (await api.get<Page<Producer>>('/producers', { params: { q: input || undefined, size: 20 } })).data,
  });

  return (
    <Autocomplete
      size="small"
      options={list.data?.content ?? []}
      loading={list.isLoading}
      value={value}
      onChange={(_, v) => onChange(v)}
      onInputChange={(_, v) => setInput(v)}
      getOptionLabel={(o) => `${o.producerNumber} · ${o.producerName}`}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      renderInput={(params) => <TextField {...params} label={label} />}
    />
  );
}
