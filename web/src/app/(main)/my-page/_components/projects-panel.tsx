import { Rocket } from "lucide-react";

import { ProjectCard, type Project } from "@/components/project/project-card";
import { EmptyState } from "@/components/shared/empty-state";

export function ProjectsPanel({ projects }: { projects: Project[] }) {
  if (projects.length === 0) {
    return (
      <EmptyState
        icon={Rocket}
        title="아직 참여 중인 프로젝트가 없습니다"
      />
    );
  }
  return (
    <section className="flex flex-col gap-4">
      {projects.map((p) => (
        <ProjectCard key={p.id} project={p} />
      ))}
    </section>
  );
}
