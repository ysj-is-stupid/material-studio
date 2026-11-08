import { useQuery } from "@tanstack/react-query";
import { getIdentity, getTasks, Task } from "./api";

export function useIdentity() {
  return useQuery({ queryKey: ["identity"], queryFn: ({ signal }) => getIdentity(signal), staleTime: Infinity });
}

export function useTasks(viewerId?: string) {
  return useQuery({
    queryKey: ["tasks", viewerId],
    queryFn: ({ signal }) => getTasks(signal),
    enabled: Boolean(viewerId),
    refetchInterval: (query) => {
      const items = query.state.data?.items as Task[] | undefined;
      return items?.some((task) => task.status === "QUEUED" || task.status === "RUNNING") ? 1500 : false;
    },
  });
}
